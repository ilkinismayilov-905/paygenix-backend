package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Promo stories: admins publish, every user sees them on the dashboard; users cannot publish.
class PromoStoryTest extends IntegrationTest {

    @MockitoBean MinioClient minio;

    @BeforeEach
    void presign() throws Exception {
        reset(minio);
        when(minio.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://storage.test/s?sig=1");
    }

    private ResultActions publish(User admin, String type, String hours) throws Exception {
        var req = multipart("/api/v1/admin/stories").file(com.example.paygenix.support.TestFiles.file("file", type, 16))
                .header("Authorization", bearer(admin));
        return mockMvc.perform(hours == null ? req : req.param("hours", hours));
    }

    private ResultActions feed(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/stories/feed").header("Authorization", bearer(user)));
    }

    private Long publishedId(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    @Test
    void everyUserSeesPublishedPromosAndWhichTheyViewed() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        Long image = publishedId(publish(admin, "image/png", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mediaType").value("IMAGE")));
        Long video = publishedId(publish(admin, "video/mp4", "2").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mediaType").value("VIDEO")));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio, org.mockito.Mockito.times(2)).putObject(put.capture());
        assertThat(put.getAllValues()).allSatisfy(a -> assertThat(a.object()).startsWith("stories/" + admin.getId() + "/"));

        User user = createUser();
        feed(user).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].username").value("Paygenix"))
                .andExpect(jsonPath("$.data.groups[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.groups[0].stories[?(@.storyId == %d)].mediaUrl".formatted(image)).value(hasItem("https://storage.test/s?sig=1")));

        mockMvc.perform(post("/api/v1/stories/{id}/view", image).header("Authorization", bearer(user))).andExpect(status().isOk());
        feed(user).andExpect(jsonPath("$.data.groups[0].stories[?(@.storyId == %d)].viewed".formatted(image)).value(hasItem(true)))
                .andExpect(jsonPath("$.data.groups[0].stories[?(@.storyId == %d)].viewed".formatted(video)).value(hasItem(false)));
        feed(createUser()).andExpect(jsonPath("$.data.groups[0].stories[?(@.storyId == %d)].viewed".formatted(image)).value(hasItem(false)));

        mockMvc.perform(get("/api/v1/admin/stories").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].views".formatted(image)).value(hasItem(1)));
    }

    @Test
    void takingAPromoDownHidesItAndRemovesTheFile() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        Long id = publishedId(publish(admin, "image/jpeg", null));

        // another admin may take it down: promos belong to the team
        mockMvc.perform(delete("/api/v1/admin/stories/{id}", id).header("Authorization", bearer(createUser(Role.ADMIN_ROLE))))
                .andExpect(status().isOk());
        verify(minio).removeObject(any(RemoveObjectArgs.class));
        feed(createUser()).andExpect(jsonPath("$.data.groups[*].stories[*].storyId").value(not(hasItem(id.intValue()))));
        mockMvc.perform(delete("/api/v1/admin/stories/{id}", id).header("Authorization", bearer(admin))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/stories/{id}/view", id).header("Authorization", bearer(createUser()))).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsPublishAndOnlyMediaFiles() throws Exception {
        User user = createUser();
        publish(user, "image/png", null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stories").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/stories").file(new MockMultipartFile("file", "x", "image/png", new byte[]{1}))
                .param("mediaType", "IMAGE").header("Authorization", bearer(user))).andExpect(status().is4xxClientError());

        User admin = createUser(Role.ADMIN_ROLE);
        publish(admin, "text/html", null).andExpect(status().isBadRequest());
        publish(admin, "image/png", "0").andExpect(status().isBadRequest());
        publish(admin, "image/png", "169").andExpect(status().isBadRequest());
        verify(minio, org.mockito.Mockito.never()).putObject(any());
    }

    @Test
    void noPromosIsAnEmptyFeed() throws Exception {
        List<Long> live = jdbc.queryForList("SELECT id FROM stories WHERE visibility = 'PUBLIC' AND NOT deleted AND expires_at > now()", Long.class);
        live.forEach(id -> jdbc.update("UPDATE stories SET deleted = true WHERE id = ?", id));
        feed(createUser()).andExpect(status().isOk()).andExpect(jsonPath("$.data.groups.length()").value(0));
    }
}
