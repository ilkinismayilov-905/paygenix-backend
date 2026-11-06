package com.example.paygenix.media;

import com.example.paygenix.support.IntegrationTest;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

// Presigning is local signing: it must work while the storage is unreachable (the tests point at a closed port).
class PresignOfflineTest extends IntegrationTest {

    @Autowired MinioClient minio;

    @Test
    void presignNeedsNoStorageRoundTrip() throws Exception {
        String url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket("stories").object("stories/1/x.jpg").expiry(60).build());
        assertThat(url).startsWith("http://localhost:9000/stories/stories/1/x.jpg?").contains("X-Amz-Signature=");
    }
}
