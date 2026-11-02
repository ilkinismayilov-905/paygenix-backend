package com.example.paygenix.media;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileMediaStorage extends AbstractMediaStorage {

    @Value("${minio.bucket.pp}")
    private String bucket;

    public ProfileMediaStorage(MinioClient minioClient) {
        super(minioClient);
    }

    public String uploadProfileImage(MultipartFile file, Long userId) {
        return uploadForUser(file, bucket, "profiles", userId);
    }
}
