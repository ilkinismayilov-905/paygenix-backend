package com.example.paygenix.service;

import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
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
        return uploadForUser(file, bucket, "uploads", userId, MEDIA_TYPES);
    }

    // Stories are only shown to their author today (no follower model), so a user may presign only keys under their own id.
    // 404, not 403, so other users' keys cannot be probed.
    public String getOwnDownloadUrl(Long userId, String objectName) {
        if (objectName == null || !objectName.matches("(stories|uploads)/" + userId + "/[0-9a-f-]{36}\\.[a-z0-9]{1,10}")) {
            throw new NotFoundException("Media not found");
        }
        return getDownloadUrl(objectName);
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
