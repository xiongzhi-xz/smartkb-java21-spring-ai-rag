package com.smartkb.infrastructure.objectstorage;

import com.smartkb.application.port.outbound.ObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO/S3 兼容对象存储适配器。
 */
@Slf4j
@Component
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioObjectStorage(
            MinioClient minioClient,
            @Value("${smartkb.object-storage.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, InputStream content, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("对象上传失败: " + objectKey, e);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("对象读取失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("对象删除失败: " + objectKey, e);
        }
    }

    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("已创建 MinIO bucket: {}", bucket);
        }
    }
}
