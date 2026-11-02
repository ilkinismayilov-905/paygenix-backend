package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.service.MediaService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaStorageTest {

    private final MinioClient minio = mock(MinioClient.class);

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    private <T> T withBucket(T storage) {
        ReflectionTestUtils.setField(storage, "bucket", "stories");
        return storage;
    }

    // T-095: the client's file name never becomes the object key, so users cannot overwrite each other's objects.
    @Test
    void objectKeyIsGeneratedPerUser() {
        String key = withBucket(new StoryMediaStorage(minio)).uploadStoryMedia(file("../other/victim.jpg"), 7L);

        assertThat(key).matches("stories/7/[0-9a-f-]{36}\\.jpg");
        assertThat(withBucket(new MediaService(minio)).upload(file("photo.png"), 7L))
                .matches("uploads/7/[0-9a-f-]{36}\\.png");
    }

    // T-095: a bad file name is the client's fault (400), not a retryable storage outage.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"noextension", "trailingdot.", "a.b/c"})
    void invalidFileNameIsRejectedBeforeStorage(String name) throws Exception {
        assertThatThrownBy(() -> withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(file(name), 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(minio, never()).putObject(any());
    }

    // T-083: a storage outage is reported as a retryable 503, not a generic 500.
    @Test
    void storageFailureIsServiceUnavailable() throws Exception {
        when(minio.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("connection refused"));

        assertUnavailable(() -> withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(file("photo.jpg"), 1L));
        assertUnavailable(() -> withBucket(new MediaService(minio)).upload(file("photo.jpg"), 1L));
    }

    private static void assertUnavailable(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.getErrorCode()).isEqualTo("MEDIA_UPLOAD_FAILED");
            assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(30));
        });
    }
}
