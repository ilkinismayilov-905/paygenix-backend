package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
public abstract class AbstractMediaStorage {
    protected final MinioClient minioClient;

    protected AbstractMediaStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // shortcut: trusts the declared type (no content sniffing); add magic-byte detection before serving files to other users.
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/quicktime", "video/webm");

    // The key is generated, never taken from the client, so one user cannot overwrite another user's object.
    protected String uploadForUser(MultipartFile file, String bucket, String prefix, Long userId) {
        String type = file.getContentType();
        if (type == null || !ALLOWED_CONTENT_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            throw ApiException.validation("Unsupported media type");
        }
        String objectName = "%s/%d/%s%s".formatted(prefix, userId, UUID.randomUUID(), extension(file.getOriginalFilename()));
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

    private static String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot);
        if (!extension.matches("\\.[A-Za-z0-9]{1,10}")) {
            throw ApiException.validation("Invalid file name");
        }
        return extension.toLowerCase(Locale.ROOT);
    }
}
