package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Profile photos: stored in the profile bucket, shown to the owner through a presigned link in GET /api/v1/profile/me.
class ProfilePhotoTest extends IntegrationTest {

    @MockitoBean MinioClient minio;

    @BeforeEach
    void presign() throws Exception {
        reset(minio);
        when(minio.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://storage.test/p?sig=1");
    }

    private ResultActions upload(User user, String type, int bytes) throws Exception {
        return mockMvc.perform(multipart("/api/v1/profile/photo").file(com.example.paygenix.support.TestFiles.file("file", type, bytes))
                .header("Authorization", bearer(user)));
    }

    private void saveProfile(User user) throws Exception {
        mockMvc.perform(post("/api/v1/profile").header("Authorization", bearer(user)).contentType("application/json")
                        .content("""
                                {"fullName":"Nigar Aliyeva","phoneNumber":"+994501234567","dateOfBirth":"02-03-1995","gender":"FEMALE"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void theOwnerSeesTheirPhotoAndAReplacementRemovesTheOldFile() throws Exception {
        User user = createUser();
        saveProfile(user);
        upload(user, "image/png", 100).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value("https://storage.test/p?sig=1"));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(put.capture());
        assertThat(put.getValue().bucket()).isEqualTo("profile");
        String first = put.getValue().object();
        assertThat(first).matches("profiles/" + user.getId() + "/[0-9a-f-]{36}\\.png");

        mockMvc.perform(get("/api/v1/profile/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.photoUrl").value("https://storage.test/p?sig=1"));

        upload(user, "image/jpeg", 100).andExpect(status().isOk());
        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minio).removeObject(removed.capture());
        assertThat(removed.getValue().object()).isEqualTo(first);

        mockMvc.perform(delete("/api/v1/profile/photo").header("Authorization", bearer(user))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/profile/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist());
    }

    @Test
    void needsASavedProfileAnImageAndAtMostFiveMegabytes() throws Exception {
        User user = createUser();
        upload(user, "image/png", 100).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        saveProfile(user);
        upload(user, "video/mp4", 100).andExpect(status().isBadRequest());
        upload(user, "image/png", 5 * 1024 * 1024 + 1).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
        verify(minio, never()).putObject(any());
    }

    @Test
    void storageDownLeavesTheProfileReadableWithoutAPhoto() throws Exception {
        User user = createUser();
        saveProfile(user);
        upload(user, "image/png", 100).andExpect(status().isOk());
        when(minio.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenThrow(new RuntimeException("down"));
        mockMvc.perform(get("/api/v1/profile/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Nigar Aliyeva"))
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist());
    }

    @Test
    void theGenericUploadAndDownloadEndpointsAreGone() throws Exception {
        User user = createUser();
        mockMvc.perform(multipart("/api/v1/user/media").file(new MockMultipartFile("file", "x", "image/png", new byte[]{1}))
                .header("Authorization", bearer(user))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/user/media-download").param("objectName", "uploads/1/x.png")
                .header("Authorization", bearer(user))).andExpect(status().isNotFound());
    }
}
