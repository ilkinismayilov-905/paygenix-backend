package com.example.paygenix.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MediaService {

    private final MinioClient minioClient;


    private String bucket = "stories";

    @Value("${story.presign-seconds}")
    private int presignSeconds;

    public String upload(MultipartFile file) {

        try {
            String fileName = file.getOriginalFilename();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(fileName)
                            .stream(
                                    file.getInputStream(),
                                    file.getSize(),
                                    -1
                            )
                            .contentType(file.getContentType())
                            .build()
            );

            return "http://localhost:9000/" + bucket + "/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException("Media upload failed", e);
        }
    }

    public String getDownloadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket("stories")
                            .object(objectName)
                            .expiry(presignSeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

