package com.example.paygenix.security;

import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-096: a user cannot get a download link for another user's media.
class MediaAccessControlTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/user/media-download", "/api/stories/media-download"})
    void otherUsersObjectIsNotFound(String path) throws Exception {
        Long victimId = createUser().getId();
        String foreignKey = "stories/" + victimId + "/3f2b9c1e-7a4d-4e8a-9b1c-2d5e6f7a8b9c.jpg";

        mockMvc.perform(get(path).param("objectName", foreignKey).header("Authorization", bearer(createUser())))
                .andExpect(status().isNotFound());
    }
}
