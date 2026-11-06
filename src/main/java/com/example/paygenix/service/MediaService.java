package com.example.paygenix.service;

import com.example.paygenix.exceptions.ApiException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Presigned links to promo story media (the stories bucket). */
@Slf4j
@Service
public class MediaService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.st}")
    private String bucket;

    @Value("${story.presign-seconds}")
    private int presignSeconds;

    public MediaService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String getDownloadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(presignSeconds)
                            .build()
            );
        } catch (Exception e) {
            log.error("Presigning a download URL in bucket {} failed", bucket, e);
            throw ApiException.mediaUnavailable();
        }
    }
}
