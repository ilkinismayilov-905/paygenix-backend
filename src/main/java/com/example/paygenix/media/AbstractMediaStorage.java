package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractMediaStorage {
    protected final MinioClient minioClient;

    protected AbstractMediaStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // shortcut: trusts the declared type (no content sniffing); media is only served back to its owner today,
    // add magic-byte detection before serving files to other users.
    // The extension comes from the whitelisted type, never from the client's file name (x.html sent as image/jpeg is stored as .jpg).
    protected static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif");
    protected static final Map<String, String> MEDIA_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif",
            "video/mp4", ".mp4", "video/quicktime", ".mov", "video/webm", ".webm");

    // The key is generated, never taken from the client, so one user cannot overwrite another user's object.
    protected String uploadForUser(MultipartFile file, String bucket, String prefix, Long userId, Map<String, String> allowedTypes) {
        String type = file.getContentType();
        String extension = type == null ? null : allowedTypes.get(type.toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw ApiException.validation("Unsupported media type");
        }
        String objectName = "%s/%d/%s%s".formatted(prefix, userId, UUID.randomUUID(), extension);
        uploadInternal(file, bucket, objectName);
        return objectName;
    }

    protected String uploadInternal(
            MultipartFile file,
            String bucket,
            String objectName
    ) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return objectName;

        } catch (Exception e) {
            log.error("Media upload to bucket {} failed", bucket, e);
            throw ApiException.mediaUploadFailed();
        }
    }
}
