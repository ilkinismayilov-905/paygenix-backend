package com.example.paygenix.service;

import com.example.paygenix.exceptions.ApiException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
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
            log.error("Media upload to bucket {} failed", bucket, e);
            throw ApiException.mediaUploadFailed();
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

