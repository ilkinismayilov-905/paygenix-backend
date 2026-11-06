package com.example.paygenix.media;

import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.service.MediaService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        return new MockMultipartFile("file", name, "image/jpeg", com.example.paygenix.support.TestFiles.content("image/jpeg", 8));
    }

    private <T> T withBucket(T storage) {
        ReflectionTestUtils.setField(storage, "bucket", "stories");
        return storage;
    }

    // T-095: the client's file name never becomes the object key, so users cannot overwrite each other's objects.
    @Test
    void objectKeyIsGeneratedPerUser() throws Exception {
        String key = withBucket(new StoryMediaStorage(minio)).uploadStoryMedia(file("../other/victim.JPG"), 7L);

        assertThat(key).matches("stories/7/[0-9a-f-]{36}\\.jpg");
        ArgumentCaptor<PutObjectArgs> sent = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(sent.capture());
        assertThat(sent.getValue().object()).isEqualTo(key);
        assertThat(withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(file("photo.png"), 7L)) // declared image/jpeg wins over the name
                .matches("profiles/7/[0-9a-f-]{36}\\.jpg");
    }

    // T-098: the extension comes from the whitelisted content type, never from the client's file name.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"noextension", "page.html", "a.b/c", "evil.svg"})
    void extensionComesFromContentType(String name) throws Exception {
        assertThat(withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(file(name), 1L))
                .matches("profiles/1/[0-9a-f-]{36}\\.jpg");
    }

    // T-098: a profile photo must be an image; videos are only accepted for stories and generic uploads.
    @Test
    void profilePhotoRejectsVideo() throws Exception {
        MockMultipartFile video = new MockMultipartFile("file", "clip.mp4", "video/mp4", com.example.paygenix.support.TestFiles.content("video/mp4", 16));
        assertThatThrownBy(() -> withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(video, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(minio, never()).putObject(any());
        assertThat(withBucket(new StoryMediaStorage(minio)).uploadStoryMedia(video, 1L)).endsWith(".mp4");
        MockMultipartFile withParams = new MockMultipartFile("file", "a", "Image/PNG; charset=binary", com.example.paygenix.support.TestFiles.content("image/png", 16));
        assertThat(withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(withParams, 1L)).endsWith(".png");
    }

    // the first bytes must match the declared type: a script sent as image/jpeg is refused
    @Test
    void contentThatDoesNotMatchItsTypeIsRejected() {
        MockMultipartFile fake = new MockMultipartFile("file", "a.jpg", "image/jpeg", "<script>".getBytes());
        assertThatThrownBy(() -> withBucket(new StoryMediaStorage(minio)).uploadStoryMedia(fake, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // T-096: only image/video types the app serves are stored.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/octet-stream"})
    void unsupportedContentTypeIsRejected(String type) throws Exception {
        MockMultipartFile html = new MockMultipartFile("file", "page.jpg", type, new byte[]{1});
        assertThatThrownBy(() -> withBucket(new StoryMediaStorage(minio)).uploadStoryMedia(html, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(minio, never()).putObject(any());
    }


    // T-083: a storage outage is reported as a retryable 503, not a generic 500.
    @Test
    void storageFailureIsServiceUnavailable() throws Exception {
        when(minio.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("connection refused"));

        assertUnavailable(() -> withBucket(new ProfileMediaStorage(minio)).uploadProfileImage(file("photo.jpg"), 1L));
        assertUnavailable(() -> withBucket(new StoryMediaStorage(minio)).uploadStoryMedia(file("photo.jpg"), 1L));
    }

    @Test
    void presignFailureIsServiceUnavailable() throws Exception {
        when(minio.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenThrow(new IOException("connection refused"));

        assertUnavailable(() -> withBucket(new MediaService(minio)).getDownloadUrl("stories/1/x.jpg"), "MEDIA_UNAVAILABLE");
    }

    private static void assertUnavailable(ThrowingCallable call) {
        assertUnavailable(call, "MEDIA_UPLOAD_FAILED");
    }

    private static void assertUnavailable(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(30));
        });
    }
}
