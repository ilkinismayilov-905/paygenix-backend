package com.example.paygenix.media;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
}
