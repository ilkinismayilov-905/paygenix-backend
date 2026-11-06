package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-113: a user reads their own profile; GET /api/v1/profile (all profiles) stays admin-only.
class OwnProfileTest extends IntegrationTest {

    @Test
    void beforeTheFirstSaveOnlyNameAndEmailAreSet() throws Exception {
        User user = createUser();
        mockMvc.perform(get("/api/v1/profile/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.fullName").value(user.getDisplayName()))
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
    }

    @Test
    void returnsTheSavedProfileOfTheCallerOnly() throws Exception {
        User me = createUser();
        User other = createUser();
        save(other, "Other Person", "+994551112233");
        save(me, "Nigar Aliyeva", "+994501234567");

        mockMvc.perform(get("/api/v1/profile/me").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Nigar Aliyeva"))
                .andExpect(jsonPath("$.data.phoneNumber").value("+994501234567"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("02-03-1995"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.email").value(me.getEmail()));
    }

    @Test
    void needsASignIn() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")).andExpect(status().isUnauthorized());
    }

    private void save(User user, String name, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/profile").header("Authorization", bearer(user)).contentType("application/json")
                        .content("""
                                {"fullName":"%s","phoneNumber":"%s","dateOfBirth":"02-03-1995","gender":"FEMALE"}""".formatted(name, phone)))
                .andExpect(status().isCreated());
    }
}
