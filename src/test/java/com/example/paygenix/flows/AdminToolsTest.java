package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminToolsTest extends IntegrationTest {

    @Test
    void findUsersByPartOfTheirEmailOrUsername() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        User user = createUser();
        String part = user.getPublicUsername().substring(4).toUpperCase(); // case does not matter
        mockMvc.perform(get("/api/admin/users").param("query", part).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(user.getId().intValue())))
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].username".formatted(user.getId())).value(hasItem(user.getPublicUsername())));
        // a LIKE wildcard is a literal character, not "match everything"
        mockMvc.perform(get("/api/admin/users").param("query", "%").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void aLockedUserIsSignedOutAtOnceAndCanBeUnlocked() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        User user = createUser();
        String token = bearer(user);
        mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/{id}/lock", user.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("LOCKED"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        mockMvc.perform(post("/api/admin/users/{id}/unlock", user.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void notYourselfNotAdminsAndOnlyForAdmins() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        mockMvc.perform(post("/api/admin/users/{id}/lock", admin.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_LOCK_SELF"));
        mockMvc.perform(post("/api/admin/users/{id}/lock", createUser(Role.ADMIN_ROLE).getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_TARGET"));
        User user = createUser();
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/{id}/lock", createUser().getId()).header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsAreListedByStatusAndMoveOn() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        User user = createUser();
        mockMvc.perform(post("/api/reports").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"CARD_NOT_WORKING\",\"message\":\"Declined\",\"referenceId\":\"card 1234\"}"))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM problem_reports WHERE user_id = ?", Long.class, user.getId());

        mockMvc.perform(get("/api/admin/reports").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].username".formatted(id)).value(hasItem(user.getPublicUsername())))
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].referenceId".formatted(id)).value(hasItem("card 1234")));
        mockMvc.perform(patch("/api/admin/reports/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESOLVED"));
        mockMvc.perform(get("/api/admin/reports").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.items[*].id").value(not(hasItem(id.intValue()))));
        mockMvc.perform(get("/api/admin/reports").param("status", "RESOLVED").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(id.intValue())));
        mockMvc.perform(patch("/api/admin/reports/{id}", id).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}")).andExpect(status().isForbidden());
    }
}
