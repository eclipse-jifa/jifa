/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.jifa.server.domain.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.eclipse.jifa.server.TestController;
import org.eclipse.jifa.server.enums.FileTransferMethod;
import org.eclipse.jifa.server.enums.FileType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TestController.class)
public class TestFileTransferRequest {

    @Autowired
    private Validator validator;

    @Test
    public void test() {
        FileTransferRequest request = new FileTransferRequest();
        Set<ConstraintViolation<FileTransferRequest>> result = validator.validate(request);
        shouldBeIllegal(result, "method", "type");

        request = new FileTransferRequest();
        request.setType(FileType.HEAP_DUMP);
        result = validator.validate(request);
        shouldBeIllegal(result, "method");

        request = new FileTransferRequest();
        request.setType(FileType.GC_LOG);
        request.setMethod(FileTransferMethod.OSS);
        result = validator.validate(request);
        shouldBeIllegal(result, "ossEndpoint", "ossAccessKeyId", "ossSecretAccessKey", "ossBucketName", "ossObjectKey");

        request = new FileTransferRequest();
        request.setType(FileType.GC_LOG);
        request.setMethod(FileTransferMethod.OSS);
        request.setOssEndpoint("endpoint");
        request.setOssAccessKeyId("accessKeyId");
        request.setOssSecretAccessKey("secretAccessKey");
        request.setOssBucketName("bucketName");
        result = validator.validate(request);
        shouldBeIllegal(result, "ossObjectKey");

        request = new FileTransferRequest();
        request.setType(FileType.GC_LOG);
        request.setMethod(FileTransferMethod.OSS);
        request.setOssEndpoint("endpoint");
        request.setOssAccessKeyId("accessKeyId");
        request.setOssSecretAccessKey("secretAccessKey");
        request.setOssBucketName("bucketName");
        request.setOssObjectKey("objectKey");
        result = validator.validate(request);
        assertEquals(0, result.size());

        request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.S3);
        result = validator.validate(request);
        shouldBeIllegal(result, "s3Region", "s3AccessKey", "s3SecretKey", "s3BucketName", "s3ObjectKey");

        request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.S3);
        request.setS3ObjectKey("objectKey");
        result = validator.validate(request);
        shouldBeIllegal(result, "s3Region", "s3AccessKey", "s3SecretKey", "s3BucketName");

        request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.S3);
        request.setS3Region("region");
        request.setS3AccessKey("accessKey");
        request.setS3SecretKey("secretKey");
        request.setS3BucketName("bucketName");
        request.setS3ObjectKey("objectKey");
        result = validator.validate(request);
        assertEquals(0, result.size());

        request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.SCP);
        result = validator.validate(request);
        shouldBeIllegal(result, "scpUser", "scpHostname", "scpSourcePath");

        request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.SCP);
        request.setScpUser("user");
        request.setScpHostname("hostname");
        result = validator.validate(request);
        shouldBeIllegal(result, "scpSourcePath");

        request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.SCP);
        request.setScpUser("user");
        request.setScpHostname("hostname");
        request.setScpSourcePath("sourcePath");
        result = validator.validate(request);
        assertEquals(0, result.size());

        request = new FileTransferRequest();
        request.setType(FileType.GC_LOG);
        request.setMethod(FileTransferMethod.URL);
        result = validator.validate(request);
        shouldBeIllegal(result, "url");

        request = new FileTransferRequest();
        request.setType(FileType.GC_LOG);
        request.setMethod(FileTransferMethod.URL);
        request.setUrl("url");
        result = validator.validate(request);
        assertEquals(0, result.size());
    }

    private void shouldBeIllegal(Set<ConstraintViolation<FileTransferRequest>> result, String... properties) {
        assertEquals(properties.length, result.size());
        for (String property : properties) {
            assertEquals(1, result.stream().filter(violation -> property.equals(violation.getPropertyPath().toString())).count());
        }
    }
}
