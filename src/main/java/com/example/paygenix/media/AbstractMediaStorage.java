package com.example.paygenix.media;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.web.multipart.MultipartFile;

public abstract class AbstractMediaStorage {
    protected final MinioClient minioClient;

    protected AbstractMediaStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    protected String uploadInternal(
            MultipartFile file,
            String bucket,
            String objectName
    ) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return objectName;

        } catch (Exception e) {
            throw new RuntimeException("Media upload failed", e);
        }
    }
}
