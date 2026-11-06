package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractMediaStorage {
    protected final MinioClient minioClient;

    protected AbstractMediaStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // The extension comes from the whitelisted type, never from the client's file name (x.html sent as image/jpeg is stored
    // as .jpg), and the first bytes must match that type: KYC documents and promo stories are shown to other people.
    protected static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif");
    protected static final Map<String, String> MEDIA_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif",
            "video/mp4", ".mp4", "video/quicktime", ".mov", "video/webm", ".webm");

    // The key is generated, never taken from the client, so one user cannot overwrite another user's object.
    protected String uploadForUser(MultipartFile file, String bucket, String prefix, Long userId, Map<String, String> allowedTypes) {
        String extension = checkedExtension(file, allowedTypes);
        String objectName = "%s/%d/%s%s".formatted(prefix, userId, UUID.randomUUID(), extension);
        uploadInternal(file, bucket, objectName);
        return objectName;
    }

    /** The stored extension for an allowed, content-matching file; VALIDATION_FAILED otherwise. Nothing is stored. */
    protected static String checkedExtension(MultipartFile file, Map<String, String> allowedTypes) {
        String type = file.getContentType();
        // Parameters such as "; charset=binary" do not change the media type.
        String extension = type == null ? null : allowedTypes.get(type.split(";")[0].trim().toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw ApiException.validation("Unsupported media type");
        }
        if (!contentMatches(file, extension)) {
            throw ApiException.validation("The file content is not a " + extension.substring(1).toUpperCase(Locale.ROOT));
        }
        return extension;
    }

    private static boolean contentMatches(MultipartFile file, String extension) {
        byte[] head = new byte[12];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        return switch (extension) {
            case ".jpg" -> n >= 3 && starts(head, 0, 0xFF, 0xD8, 0xFF);
            case ".png" -> n >= 8 && starts(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case ".gif" -> n >= 4 && starts(head, 0, 'G', 'I', 'F', '8');
            case ".webp" -> n >= 12 && starts(head, 0, 'R', 'I', 'F', 'F') && starts(head, 8, 'W', 'E', 'B', 'P');
            case ".mp4", ".mov" -> n >= 8 && (starts(head, 4, 'f', 't', 'y', 'p') || starts(head, 4, 'm', 'o', 'o', 'v')
                    || starts(head, 4, 'm', 'd', 'a', 't') || starts(head, 4, 'w', 'i', 'd', 'e'));
            case ".webm" -> n >= 4 && starts(head, 0, 0x1A, 0x45, 0xDF, 0xA3);
            default -> false;
        };
    }

    private static boolean starts(byte[] data, int offset, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
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
