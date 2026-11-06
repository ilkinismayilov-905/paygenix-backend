package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Profile photos, in their own bucket. Only the owner gets a link, through GET /api/profile/me. */
@Slf4j
@Service
public class ProfileMediaStorage extends AbstractMediaStorage {

    static final int PRESIGN_SECONDS = 600;

    @Value("${minio.bucket.pp}")
    private String bucket;

    public ProfileMediaStorage(MinioClient minioClient) {
        super(minioClient);
    }

    public String uploadProfileImage(MultipartFile file, Long userId) {
        return uploadForUser(file, bucket, "profiles", userId, IMAGE_TYPES);
    }

    public String presign(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectName).expiry(PRESIGN_SECONDS).build());
        } catch (Exception e) {
            log.error("Presigning a profile photo in bucket {} failed", bucket, e);
            throw ApiException.mediaUnavailable();
        }
    }

    // Best effort: a photo that was replaced or removed; a failure only leaves an unused object behind.
    public void deleteQuietly(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            log.warn("Removing object {} from bucket {} failed; it stays in storage", objectName, bucket, e);
        }
    }
}
