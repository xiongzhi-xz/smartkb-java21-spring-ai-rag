package com.smartkb.infrastructure.objectstorage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 */
@Configuration
public class ObjectStorageConfig {

    @Bean
    MinioClient minioClient(
            @Value("${smartkb.object-storage.endpoint}") String endpoint,
            @Value("${smartkb.object-storage.access-key}") String accessKey,
            @Value("${smartkb.object-storage.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
