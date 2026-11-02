package com.example.paygenix.media;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class StoryMediaStorage extends AbstractMediaStorage {

    @Value("${minio.bucket.st}")
    private String bucket;

    public StoryMediaStorage(MinioClient minioClient) {
        super(minioClient);
    }

    public String uploadStoryMedia(MultipartFile file, Long userId) {
        return uploadForUser(file, bucket, "stories", userId);
    }

    // Best effort: called while another error is already being reported, so a failure here is only logged.
    public void deleteQuietly(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            log.warn("Removing orphan object {} from bucket {} failed", objectName, bucket, e);
        }
    }
}
