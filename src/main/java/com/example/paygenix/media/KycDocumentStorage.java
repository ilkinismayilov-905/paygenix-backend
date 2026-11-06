package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Identity documents, in their own private bucket (T-070). The owner never gets a link back; reviewers get a
 * short-lived presigned URL per look.
 */
@Slf4j
@Service
public class KycDocumentStorage extends AbstractMediaStorage {

    static final Map<String, String> DOCUMENT_TYPES = Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");
    static final int PRESIGN_SECONDS = 300;

    @Value("${minio.bucket.kyc:kyc}")
    private String bucket;

    public KycDocumentStorage(MinioClient minioClient) {
        super(minioClient);
    }

    public String upload(MultipartFile file, Long userId) {
        return uploadForUser(file, bucket, "kyc", userId, DOCUMENT_TYPES);
    }

    public String presign(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectKey).expiry(PRESIGN_SECONDS).build());
        } catch (Exception e) {
            log.error("Presigning a KYC document in bucket {} failed", bucket, e);
            throw ApiException.mediaUnavailable();
        }
    }
}
