package com.example.paygenix.media;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class ProfileMediaStorage extends AbstractMediaStorage {

    @Value("${minio.bucket.pp}")
    private String bucket;

    public ProfileMediaStorage(MinioClient minioClient) {
        super(minioClient);
    }

    public String uploadProfileImage(MultipartFile file, Long userId) {

        String extension = getExtension(file.getOriginalFilename());

        String objectName = "profiles/%d/%s%s"
                .formatted(userId, UUID.randomUUID(), extension);

        uploadInternal(file, bucket, objectName);

        return objectName;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
