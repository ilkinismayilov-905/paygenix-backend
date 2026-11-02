package com.example.paygenix.service;

import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.media.AbstractMediaStorage;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MediaService extends AbstractMediaStorage {

    @Value("${minio.bucket.st}")
    private String bucket;

    @Value("${story.presign-seconds}")
    private int presignSeconds;

    public MediaService(MinioClient minioClient) {
        super(minioClient);
    }

    public String upload(MultipartFile file, Long userId) {
        return uploadForUser(file, bucket, "uploads", userId);
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
