package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.service.MediaService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// T-083: a storage outage is reported as 503 MEDIA_UPLOAD_FAILED, not a generic 500.
class MediaUploadFailureTest {

    private final MinioClient minio = mock(MinioClient.class);
    private final MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void storageIsDown() throws Exception {
        when(minio.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("connection refused"));
    }

    @Test
    void profileImageUploadFailureIsServiceUnavailable() {
        ProfileMediaStorage storage = new ProfileMediaStorage(minio);
        ReflectionTestUtils.setField(storage, "bucket", "profiles");

        assertMediaUploadFailed(() -> storage.uploadProfileImage(file, 1L));
    }

    @Test
    void storyMediaUploadFailureIsServiceUnavailable() {
        assertMediaUploadFailed(() -> new MediaService(minio).upload(file));
    }

    private static void assertMediaUploadFailed(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getErrorCode()).isEqualTo("MEDIA_UPLOAD_FAILED");
                });
    }
}
