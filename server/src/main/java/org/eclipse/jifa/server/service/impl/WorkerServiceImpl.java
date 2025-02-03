/********************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.server.service.impl;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.eclipse.jifa.common.domain.exception.ShouldNotReachHereException;
import org.eclipse.jifa.common.util.Validate;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.condition.Master;
import org.eclipse.jifa.server.domain.dto.FileLocation;
import org.eclipse.jifa.server.domain.dto.HttpRequestToWorker;
import org.eclipse.jifa.server.domain.entity.cluster.ElasticWorkerEntity;
import org.eclipse.jifa.server.domain.entity.cluster.FileLocationRuleEntity;
import org.eclipse.jifa.server.domain.entity.cluster.StaticWorkerEntity;
import org.eclipse.jifa.server.domain.entity.cluster.StaticWorkerLabelEntity;
import org.eclipse.jifa.server.domain.entity.cluster.WorkerEntity;
import org.eclipse.jifa.server.domain.entity.shared.file.FileEntity;
import org.eclipse.jifa.server.domain.entity.shared.user.UserEntity;
import org.eclipse.jifa.server.domain.exception.ElasticWorkerNotReadyException;
import org.eclipse.jifa.server.domain.support.JsonConvertible;
import org.eclipse.jifa.server.enums.ElasticWorkerPurpose;
import org.eclipse.jifa.server.enums.ElasticWorkerState;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.repository.ElasticWorkerRepo;
import org.eclipse.jifa.server.repository.FileLocationRuleRepo;
import org.eclipse.jifa.server.repository.StaticWorkerLabelRepo;
import org.eclipse.jifa.server.repository.StaticWorkerRepo;
import org.eclipse.jifa.server.service.ElasticWorkerScheduler;
import org.eclipse.jifa.server.service.StorageService;
import org.eclipse.jifa.server.service.UserService;
import org.eclipse.jifa.server.service.WorkerService;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.eclipse.jifa.common.domain.exception.CommonException.CE;
import static org.eclipse.jifa.common.enums.CommonErrorCode.INTERNAL_ERROR;
import static org.eclipse.jifa.common.util.GsonHolder.GSON;
import static org.eclipse.jifa.server.Constant.HTTP_API_PREFIX;
import static org.eclipse.jifa.server.domain.entity.cluster.ElasticWorkerEntity.MAX_FAILURE_MESSAGE_LENGTH;
import static org.eclipse.jifa.server.enums.ElasticWorkerPurpose.FILE_ANALYSIS;
import static org.eclipse.jifa.server.enums.ServerErrorCode.NO_AVAILABLE_LOCATION;

@Master
@Service
public class WorkerServiceImpl extends ConfigurationAccessor implements WorkerService {

    private final UserService userService;
    protected final WebClient webClient;
    private static final int DELETION_DELAY = 60;
    private final StorageService storageService;
    private final FileLocationRuleRepo fileLocationRuleRepo;
    private final StaticWorkerRepo staticWorkerRepo;
    private final StaticWorkerLabelRepo staticWorkerLabelRepo;
    private final ElasticWorkerRepo elasticWorkerRepo;
    private final ElasticWorkerScheduler elasticWorkerScheduler;
    private final TaskScheduler taskScheduler;
    private final RetryTemplate retryTemplateForAcquiringElasticWorker;

    protected WorkerServiceImpl(UserService userService,
                                StorageService storageService,
                                FileLocationRuleRepo fileLocationRuleRepo,
                                StaticWorkerRepo staticWorkerRepo, StaticWorkerLabelRepo staticWorkerLabelRepo,
                                ElasticWorkerRepo elasticWorkerRepo, ElasticWorkerScheduler elasticWorkerScheduler,
                                TaskScheduler taskScheduler) {
        this.userService = userService;
        this.storageService = storageService;
        this.fileLocationRuleRepo = fileLocationRuleRepo;
        this.staticWorkerRepo = staticWorkerRepo;
        this.staticWorkerLabelRepo = staticWorkerLabelRepo;
        this.elasticWorkerRepo = elasticWorkerRepo;
        this.elasticWorkerScheduler = elasticWorkerScheduler;
        this.taskScheduler = taskScheduler;

        HttpClient httpClient = HttpClient.create()
                                          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 16000)
                                          .doOnConnected(conn -> conn
                                                  .addHandlerLast(new ReadTimeoutHandler(Long.MAX_VALUE, TimeUnit.SECONDS))
                                                  .addHandlerLast(new WriteTimeoutHandler(Long.MAX_VALUE, TimeUnit.SECONDS)));

        final int maxInMemorySize = 10 * 1024 * 1024;
        final ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .build();
        webClient = WebClient.builder()
                             .exchangeStrategies(exchangeStrategies)
                             .clientConnector(new ReactorClientHttpConnector(httpClient))
                             .build();

        retryTemplateForAcquiringElasticWorker = new RetryTemplate();

        CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();

        TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
        timeoutRetryPolicy.setTimeout(30000);

        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(Integer.MAX_VALUE,
                                                                    Collections.singletonMap(DataIntegrityViolationException.class, true));

        compositeRetryPolicy.setPolicies(new RetryPolicy[]{
                timeoutRetryPolicy, simpleRetryPolicy
        });

        retryTemplateForAcquiringElasticWorker.setRetryPolicy(compositeRetryPolicy);
        UniformRandomBackOffPolicy backOffPolicy = new UniformRandomBackOffPolicy();
        backOffPolicy.setMinBackOffPeriod(500);
        backOffPolicy.setMaxBackOffPeriod(5000);
        retryTemplateForAcquiringElasticWorker.setBackOffPolicy(new UniformRandomBackOffPolicy());
    }

    @Override
    public FileLocation decideLocationForNewFile(UserEntity user, FileType type) {
        FileLocationRuleEntity rule = null;
        if (fileLocationRuleRepo.count() > 0) {
            // find by user and file type
            rule = fileLocationRuleRepo.findByUserAndFileType(user, type);
            if (rule == null) {
                // find by user
                rule = fileLocationRuleRepo.findByUserAndFileType(user, null);
            }
            if (rule == null) {
                // find by file type
                rule = fileLocationRuleRepo.findByUserAndFileType(null, type);
            }
            if (rule == null) {
                // default
                rule = fileLocationRuleRepo.findByUserAndFileType(null, null);
            }
        }

        Optional<StaticWorkerEntity> staticWorker;

        if (rule == null) {
            if (storageService.available()) {
                return new FileLocation(true, null);
            }

            staticWorker = staticWorkerRepo.findFirstByOrderByAvailableSpaceDesc();
        } else {

            switch (rule.getRule()) {
                case SHARED_STORAGE -> {
                    return new FileLocation(true, null);
                }

                case STATIC_WORKERS -> {
                    staticWorker = staticWorkerRepo.findFirstByOrderByAvailableSpaceDesc();
                }

                case LABELED_STATIC_WORKERS -> {
                    staticWorker = staticWorkerLabelRepo.findByLabel(rule.getLabel()).stream()
                                                        .sorted(Comparator.comparingLong(e -> e.getStaticWorker().getAvailableSpace()))
                                                        .reduce((f, s) -> s).map(StaticWorkerLabelEntity::getStaticWorker);
                }

                default -> throw new ShouldNotReachHereException();
            }
        }

        if (staticWorker.isPresent()) {
            return new FileLocation(false, staticWorker.get());
        }

        throw CE(NO_AVAILABLE_LOCATION);
    }

    @Override
    public long forwardUploadRequestToStaticWorker(StaticWorkerEntity worker, FileType type, MultipartFile file) throws Throwable {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource())
               .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : Constant.DEFAULT_FILENAME)
               .contentType(MediaType.APPLICATION_OCTET_STREAM);
        builder.part("type", type.name());

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder()
                                                              .scheme("http")
                                                              .host(worker.getHostAddress())
                                                              .port(worker.getPort())
                                                              .path(HTTP_API_PREFIX + "/files/upload");

        WebClient.RequestBodySpec spec = webClient.method(HttpMethod.POST)
                                                  .uri(uriBuilder.build());

        String jwtToken = userService.getCurrentUserJwtTokenOrNull();
        if (jwtToken != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        }

        return spec.contentType(MediaType.MULTIPART_FORM_DATA)
                   .body(BodyInserters.fromMultipartData(builder.build()))
                   .exchangeToMono(response -> {
                       if (!response.statusCode().is2xxSuccessful()) {
                           return response.createError();
                       }
                       return response.bodyToMono(String.class).map(s -> GSON.fromJson(s, Long.class));
                   }).toFuture().get();
    }

    @Override
    public Resource forwardDownloadRequestToStaticWorker(StaticWorkerEntity worker, long fileId) throws Throwable {
        Validate.isTrue(isMaster(), INTERNAL_ERROR);
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder()
                                                              .scheme("http")
                                                              .host(worker.getHostAddress())
                                                              .port(worker.getPort())
                                                              .path(HTTP_API_PREFIX + "/files/" + fileId + "/download");
        return new UrlResource(uriBuilder.build()) {
            @Override
            protected void customizeConnection(@NotNull HttpURLConnection con) throws IOException {
                super.customizeConnection(con);
                String jwtToken = userService.getCurrentUserJwtTokenOrNull();
                if (jwtToken != null) {
                    con.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + userService.getCurrentUserJwtTokenOrNull());
                }
            }
        };
    }

    @Override
    public ElasticWorkerState getElasticWorkerState(long workerId) {
        return elasticWorkerRepo.findById(workerId).orElseThrow(() -> CE(INTERNAL_ERROR))
                                .getState();
    }

    @Override
    public ElasticWorkerEntity requestElasticWorkerForAnalysisApiRequest(FileEntity target) {
        ElasticWorkerEntity worker = acquireElasticWorkerForAnalysis(target);
        return switch (worker.getState()) {
            case READY -> worker;
            case STARTING -> throw new ElasticWorkerNotReadyException(worker.getId());
            case FAILURE -> throw CE(INTERNAL_ERROR);
        };
    }

    @Override
    public <Response> Response syncRequest(WorkerEntity worker, HttpRequestToWorker<Response> request) {
        try {
            return asyncRequest(worker, request).get();
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            if (t instanceof ExecutionException ee) {
                if (ee.getCause() instanceof WebClientResponseException re) {
                    throw re;
                }
            }
            throw new RuntimeException(t);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Response> CompletableFuture<Response> asyncRequest(WorkerEntity worker, HttpRequestToWorker<Response> request) {
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder()
                                                              .scheme("http")
                                                              .host(worker.getHostAddress())
                                                              .port(worker.getPort())
                                                              .path(HTTP_API_PREFIX + "/" + request.uri());

        HttpMethod method = request.method();
        if (method == HttpMethod.GET && request.query() != null) {
            uriBuilder.queryParams(request.query());
        }

        WebClient.RequestBodySpec spec = webClient.method(method)
                                                  .uri(uriBuilder.build())
                                                  .accept(MediaType.APPLICATION_JSON);

        String jwtToken = userService.getCurrentUserJwtTokenOrNull();

        if (jwtToken != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        }

        if (request.body() != null) {
            Object body = request.body();
            String bodyJson;
            if (body instanceof JsonConvertible convertible) {
                bodyJson = convertible.toJson();
            } else {
                bodyJson = GSON.toJson(body);
            }
            byte[] bytes = bodyJson.getBytes(Constant.CHARSET);
            spec.contentType(MediaType.APPLICATION_JSON)
                .body((BodyInserter<byte[], ClientHttpRequest>) (message, context) -> {
                    DataBuffer buffer = message.bufferFactory().wrap(bytes);
                    message.getHeaders().setContentLength(bytes.length);
                    return message.writeWith(Mono.just(buffer));
                });
        }

        Class<Response> responseClass = request.responseType();
        return spec.exchangeToMono(response -> {
            if (!response.statusCode().is2xxSuccessful()) {
                return response.createError();
            }

            if (responseClass == Void.class) {
                return Mono.empty();
            }

            if (responseClass == byte[].class) {
                return (Mono<Response>) response.bodyToMono(byte[].class)
                                                .defaultIfEmpty(Constant.EMPTY_BYTE_ARRAY);
            }

            return response.bodyToMono(String.class)
                           .map(s -> GSON.fromJson(s, responseClass));
        }).toFuture();
    }

    private ElasticWorkerEntity acquireElasticWorkerForAnalysis(FileEntity target) {
        return AcquireElasticWorker(FILE_ANALYSIS, target.getId(), () -> {
            // TODO: need improvements
            long GB = 1024 * 1024 * 1024L;
            return (long) Math.max(GB, 1.3 * target.getSize());
        });
    }

    @SuppressWarnings("SameParameterValue")
    private ElasticWorkerEntity AcquireElasticWorker(ElasticWorkerPurpose purpose, long referenceId, Supplier<Long> requestedMemorySizeSupplier) {
        return retryTemplateForAcquiringElasticWorker.execute(c -> {

            Optional<ElasticWorkerEntity> optional = elasticWorkerRepo.findByPurposeAndReferenceId(purpose, referenceId);

            if (optional.isPresent()) {
                return optional.get();
            }

            ElasticWorkerEntity elasticWorker = createNewElasticWorker(purpose, referenceId);

            long requestedMemorySize = requestedMemorySizeSupplier.get();

            elasticWorkerScheduler.scheduleAsync(elasticWorker.getId(), requestedMemorySize, (hostAddress, throwable) -> {
                if (throwable != null) {
                    try {
                        elasticWorker.setState(ElasticWorkerState.FAILURE);
                        String failureMessage = throwable.getMessage();
                        if (failureMessage.length() > MAX_FAILURE_MESSAGE_LENGTH) {
                            failureMessage = failureMessage.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
                        }
                        elasticWorker.setFailureMessage(failureMessage);
                        elasticWorkerRepo.save(elasticWorker);
                    } finally {
                        taskScheduler.schedule(() -> elasticWorkerRepo.deleteById(elasticWorker.getId()),
                                               Instant.now().plusSeconds(DELETION_DELAY));
                    }
                } else {
                    elasticWorker.setHostAddress(hostAddress);
                    elasticWorker.setState(ElasticWorkerState.READY);
                    elasticWorkerRepo.save(elasticWorker);
                }
            });
            return elasticWorker;
        });
    }

    private ElasticWorkerEntity createNewElasticWorker(ElasticWorkerPurpose purpose, long referenceId) {
        ElasticWorkerEntity newElasticWorker = new ElasticWorkerEntity();
        newElasticWorker.setPurpose(purpose);
        newElasticWorker.setReferenceId(referenceId);
        newElasticWorker.setState(ElasticWorkerState.STARTING);
        newElasticWorker.setPort(config.getElasticWorkerPort());
        return elasticWorkerRepo.save(newElasticWorker);
    }
}
