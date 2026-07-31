/********************************************************************************
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.jifa.analysis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jifa.analysis.annotation.ApiMeta;
import org.eclipse.jifa.analysis.annotation.ApiParameterMeta;
import org.eclipse.jifa.analysis.annotation.Exclude;
import org.eclipse.jifa.analysis.listener.DefaultProgressListener;
import org.eclipse.jifa.analysis.listener.ProgressListener;
import org.eclipse.jifa.analysis.support.MethodNameConverter;
import org.eclipse.jifa.analysis.util.TypeParameterUtil;
import org.eclipse.jifa.common.domain.exception.ErrorCodeException;
import org.eclipse.jifa.common.util.ExecutorFactory;
import org.eclipse.jifa.common.util.Validate;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.eclipse.jifa.analysis.enums.AnalysisErrorCode.FILE_NOT_FOUND;
import static org.eclipse.jifa.analysis.listener.ProgressListener.NoOpProgressListener;

@Slf4j
public abstract class AbstractApiExecutor<Analyzer> implements ApiExecutor {

    private Set<Api> apis;

    private final Map<String, Method> apiMethodMap = new HashMap<>();

    private final Set<String> predefinedApiNames = new HashSet<>();

    private final Map<ExecutionContext, CompletableFuture<?>> activeContext = new ConcurrentHashMap<>();

    private final Map<Path, CompletableFuture<Analyzer>> buildingAnalyzer = new ConcurrentHashMap<>();

    private final Map<Path, ProgressListener> buildingAnalyzerListeners = new ConcurrentHashMap<>();

    private final Cache<Path, Analyzer> cachedAnalyzer;

    private final java.util.concurrent.Executor executor;

    protected AbstractApiExecutor() {
        loadApi();

        executor = ExecutorFactory.newExecutor(this.getClass().getSimpleName() + " Executor");

        cachedAnalyzer = Caffeine.newBuilder()
                                 .scheduler(Scheduler.systemScheduler())
                                 .softValues()
                                 .expireAfterAccess(getCacheDuration(), TimeUnit.MINUTES)
                                 .removalListener((RemovalListener<Object, Analyzer>) (key, analyzer, cause) -> cachedAnalyzerRemoved(analyzer))
                                 .build();
    }

    @Override
    public final Set<Api> apis() {
        return apis;
    }

    @Override
    public final CompletableFuture<?> execute(ExecutionContext context) {

        Method method = apiMethodMap.get(context.api());

        if (method == null) {
            throw new IllegalArgumentException("Unsupported api: " + context.api());
        }

        return activeContext.computeIfAbsent(context, ignored -> {
            boolean isPredefinedApi = predefinedApiNames.contains(context.api());
            CompletableFuture<?> receiver = isPredefinedApi
                    ? CompletableFuture.completedFuture(this)
                    : buildAnalyzer(context.target(), Collections.emptyMap());
            return receiver.thenApplyAsync(r -> {
                try {
                    return checkApiReturnValue(method.invoke(r, context.arguments()));
                } catch (RuntimeException re) {
                    throw re;
                } catch (Throwable t) {
                    throw new CompletionException(t);
                } finally {
                    activeContext.remove(context);
                }
            }, executor);
        });
    }

    private void loadApi() {
        this.apis = new HashSet<>();

        // load predefine apis
        fillPredefinedApis("needOptionsForAnalysis", Path.class);
        fillPredefinedApis("analyze", Path.class, Map.class);
        fillPredefinedApis("progressOfAnalysis", Path.class);
        fillPredefinedApis("release", Path.class);
        fillPredefinedApis("clean", Path.class);
        fillPredefinedApis("errorLog", Path.class);

        // load apis from analyzer class
        Class<Analyzer> analyzerClass = analyzerClass();
        for (Method method : analyzerClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) ||
                (!analyzerClass.isInterface() && method.getDeclaringClass() != analyzerClass) ||
                method.isAnnotationPresent(Exclude.class)) {
                continue;
            }
            String name = method.getName();
            if (methodNameConverter() != null) {
                name = methodNameConverter().convert(name);
            }
            String[] aliases = null;
            ApiMeta apiMeta = method.getAnnotation(ApiMeta.class);
            if (apiMeta != null) {
                if (StringUtils.isNotBlank(apiMeta.value())) {
                    name = apiMeta.value();
                }
                aliases = apiMeta.aliases();
            }
            // validate duplication
            Validate.isTrue(!apiMethodMap.containsKey(name), "Duplicate api name: " + name);
            if (aliases != null) {
                for (String alias : aliases) {
                    Validate.isTrue(!apiMethodMap.containsKey(alias), "Duplicate api name: " + alias);
                }
            }

            ApiParameter[] apiParameters = buildApiParameters(method);

            Set<String> aliasesSet = aliases != null ? Set.of(aliases) : Collections.emptySet();
            Api api = new Api(name, aliasesSet, apiParameters);

            apiMethodMap.put(name, method);
            for (String alias : aliasesSet) {
                apiMethodMap.put(alias, method);
            }
            apis.add(api);
        }
        this.apis = Collections.unmodifiableSet(apis);
    }

    private void fillPredefinedApis(String name, Class<?>... parameterTypes) {
        Method method;
        try {
            method = AbstractApiExecutor.class.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        ApiParameter[] apiParameters = buildApiParameters(method);
        Api api = new Api(name, Collections.emptySet(), apiParameters);
        predefinedApiNames.add(name);
        apiMethodMap.put(name, method);
        apis.add(api);
    }

    private ApiParameter[] buildApiParameters(Method method) {
        Parameter[] parameters = method.getParameters();
        ApiParameter[] apiParameters = new ApiParameter[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            ApiParameterMeta apiParameterMeta = parameter.getAnnotation(ApiParameterMeta.class);
            String parameterName = parameter.getName();
            Type type = parameter.getParameterizedType();
            boolean required = true;
            boolean targetPath = false;
            boolean comparisonTargetPath = false;
            if (apiParameterMeta != null) {
                if (StringUtils.isNotBlank(apiParameterMeta.value())) {
                    parameterName = apiParameterMeta.value();
                }
                required = apiParameterMeta.required();
                targetPath = apiParameterMeta.targetPath();
                comparisonTargetPath = apiParameterMeta.comparisonTargetPath();
            }
            Validate.isTrue(!(targetPath && comparisonTargetPath));
            if (targetPath || comparisonTargetPath) {
                Validate.isTrue(type == Path.class);
                // targetPath is always required; comparisonTargetPath may be optional
                // (required flag was already set from @ApiParameterMeta above)
                if (targetPath) {
                    required = true;
                }
            }
            if (type == Path.class) {
                Validate.isTrue(targetPath || comparisonTargetPath);
            }
            ApiParameter apiParameter = new ApiParameter(parameterName, type, required, targetPath, comparisonTargetPath);
            apiParameters[i] = apiParameter;
        }
        return apiParameters;
    }

    private CompletableFuture<Analyzer> buildAnalyzer(Path target, Map<String, String> options) {
        Analyzer analyzer = cachedAnalyzer.getIfPresent(target);

        if (analyzer != null) {
            return CompletableFuture.completedFuture(analyzer);
        }

        AtomicBoolean puttedByMe = new AtomicBoolean(false);
        CompletableFuture<Analyzer> analyzerFuture = buildingAnalyzer.computeIfAbsent(target, ignored -> {
            CompletableFuture<Analyzer> f = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    Analyzer r = cachedAnalyzer.getIfPresent(target);
                    if (r == null) {
                        ProgressListener listener = this.buildingAnalyzerListeners.get(target);
                        r = buildAnalyzer(target, options, listener != null ? listener : NoOpProgressListener);
                        cachedAnalyzer.put(target, r);
                    }
                    f.complete(r);
                } catch (Throwable e) {
                    f.completeExceptionally(e);
                }
            });
            puttedByMe.set(true);
            return f;
        });
        if (puttedByMe.get()) {
            analyzerFuture.whenComplete((r, t) -> buildingAnalyzer.remove(target));
        }
        return analyzerFuture;
    }

    protected MethodNameConverter methodNameConverter() {
        return null;
    }

    protected abstract Analyzer buildAnalyzer(Path target, Map<String, String> options, ProgressListener listener) throws Throwable;

    protected void cachedAnalyzerRemoved(Analyzer analyzer) {
    }

    private Object checkApiReturnValue(Object rv) {
        if (rv instanceof Future<?>) {
            throw new IllegalStateException("Analysis api must not return a Future");
        }
        return rv;
    }

    @SuppressWarnings("unchecked")
    private Class<Analyzer> analyzerClass() {
        return (Class<Analyzer>) TypeParameterUtil.extractActualType(this, "Analyzer");
    }

    public boolean needOptionsForAnalysis(@ApiParameterMeta(targetPath = true) Path target) {
        checkExists(target);
        return false;
    }

    public final void analyze(@ApiParameterMeta(targetPath = true) Path target,
                              @ApiParameterMeta(required = false) Map<String, String> options) {
        if (cachedAnalyzer.getIfPresent(target) != null) {
            return;
        }

        checkExists(target);

        ProgressListener progressListener = new DefaultProgressListener();

        boolean puttedByMe = buildingAnalyzerListeners.putIfAbsent(target, progressListener) == null;

        if (puttedByMe) {
            CompletableFuture<Analyzer> future = buildAnalyzer(target, options);
            future.whenComplete((analyzer, throwable) -> {
                try {
                    if (throwable != null) {
                        try {
                            log.error("Error occurred while building Analyzer: {}", throwable.getMessage());

                            File log = errorLogFile(target);
                            FileUtils.writeStringToFile(log, progressListener.log(), StandardCharsets.UTF_8, false);

                            StringWriter sw = new StringWriter();
                            throwable.printStackTrace(new PrintWriter(sw));
                            FileUtils.writeStringToFile(log, sw.toString(), StandardCharsets.UTF_8, true);
                        } catch (Throwable ignored) {
                        }
                    }
                } finally {
                    buildingAnalyzerListeners.remove(target);
                }
            });
        }
    }

    public final Progress progressOfAnalysis(@ApiParameterMeta(targetPath = true) Path target) throws IOException {
        if (cachedAnalyzer.getIfPresent(target) != null) {
            Progress progress = new Progress();
            progress.setPercent(1);
            progress.setState(Progress.State.SUCCESS);
            return progress;
        }
        ProgressListener listener = this.buildingAnalyzerListeners.get(target);
        if (listener != null) {
            Progress progress = new Progress();
            progress.setState(Progress.State.IN_PROGRESS);
            progress.setMessage(listener.log());
            progress.setPercent(listener.percent());
            return progress;
        }
        checkExists(target);
        Progress result = new Progress();
        result.setState(Progress.State.FAILURE);
        File errorLog = errorLogFile(target);
        if (errorLog.exists()) {
            result.setMessage(FileUtils.readFileToString(errorLog, StandardCharsets.UTF_8));
        }
        return result;
    }

    public void release(@ApiParameterMeta(targetPath = true) Path target) {
        cleanAndDisposeAnalyzerCache(target);
    }

    public void clean(@ApiParameterMeta(targetPath = true) Path target) {
        cleanAndDisposeAnalyzerCache(target);
        File errorLog = errorLogFile(target);
        if (errorLog.exists()) {
            if (!errorLog.delete()) {
                log.warn("Failed to delete error log file: {}", errorLog.getAbsolutePath());
            }
        }
    }

    public String errorLog(@ApiParameterMeta(targetPath = true) Path target) throws IOException {
        return FileUtils.readFileToString(errorLogFile(target), StandardCharsets.UTF_8);
    }

    protected File errorLogFile(Path path) {
        return path.resolveSibling(path.getFileName() + "-error.log").toFile();
    }

    protected final boolean isActive(Path target) {
        return cachedAnalyzer.getIfPresent(target) != null || buildingAnalyzer.containsKey(target);
    }

    /**
     * @return cache duration in minutes
     */
    protected int getCacheDuration() {
        return 8;
    }

    protected void checkExists(Path target) {
        if (!target.toFile().exists()) {
            throw new ErrorCodeException(FILE_NOT_FOUND);
        }
    }

    private void cleanAndDisposeAnalyzerCache(Path target) {
        // Dispose snapshot synchronized to prevent from some problem caused by data inconsistency.
        Analyzer analyzer = cachedAnalyzer.getIfPresent(target);
        cachedAnalyzer.invalidate(target);
        if (analyzer != null) {
            cachedAnalyzerRemoved(analyzer);
        }
    }
}