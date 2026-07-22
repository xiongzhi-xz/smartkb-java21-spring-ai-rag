package com.smartkb.infrastructure.objectstorage;

import io.minio.MinioClient;
import io.minio.GetObjectResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioObjectStorageTest {

    @Test
    void shouldUploadObjectToConfiguredBucket() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        new MinioObjectStorage(minioClient, "smartkb-documents")
                .put("documents/doc-1.md",
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                        7,
                        "text/markdown");

        verify(minioClient).putObject(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReadAndDeleteObjectFromConfiguredBucket() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.getObject(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mock(GetObjectResponse.class));

        MinioObjectStorage storage = new MinioObjectStorage(minioClient, "smartkb-documents");

        storage.get("documents/doc-1.md");
        storage.delete("documents/doc-1.md");

        verify(minioClient).getObject(org.mockito.ArgumentMatchers.any());
        verify(minioClient).removeObject(org.mockito.ArgumentMatchers.any());
    }
}
