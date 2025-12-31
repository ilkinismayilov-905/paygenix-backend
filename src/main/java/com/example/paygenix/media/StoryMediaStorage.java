package com.example.paygenix.media;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class StoryMediaStorage extends AbstractMediaStorage {

    @Value("${minio.bucket.st}")
    private String bucket;

    public StoryMediaStorage(MinioClient minioClient) {
        super(minioClient);
    }

    public String uploadStoryMedia(MultipartFile file, Long userId) {

        String extension = getExtension(file.getOriginalFilename());

        String objectName = "stories/%d/%s%s"
                .formatted(userId, UUID.randomUUID(), extension);

        uploadInternal(file, bucket, objectName);

        return objectName;
    }

    private String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf("."));
    }
}

