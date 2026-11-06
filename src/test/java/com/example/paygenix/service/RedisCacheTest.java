package com.example.paygenix.service;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.entity.EditProfile;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Gender;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-016: cached values must survive a Redis round trip (write and read back).
class RedisCacheTest extends IntegrationTest {

    @Autowired EditProfileRepository editProfileRepository;
    @Autowired CacheManager cacheManager;
    @Autowired StringRedisTemplate redis;

    @Test
    void partialProfileUpdateSucceedsAndRefreshesTheCachedList() throws Exception {
        User user = createUser();
        editProfileRepository.save(EditProfile.builder().email(user.getEmail()).fullName(user.getFullname())
                .phoneNumber("+994501234567").dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE).user(user).build());
        String admin = bearer(createUser(Role.ADMIN_ROLE));

        mockMvc.perform(get("/api/v1/profile").header("Authorization", admin)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/profile").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Cached Name\"}"))
                .andExpect(status().isCreated());

        // first call fills the cache, second one is read back from Redis
        assertThat(redis.hasKey("editProfileAll::all")).isFalse();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/profile").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].fullName", hasItem("Cached Name")));
            assertThat(redis.hasKey("editProfileAll::all")).isTrue();
        }
        assertExpiresWithin("editProfileAll::all", 600);
    }

    @Test
    void cachedLegalDocumentIsReadBack() throws Exception {
        cacheManager.getCache(CacheNames.LEGAL_DOCUMENT_BY_TYPE).evict("TERMS");
        jdbc.update("UPDATE legal_documents SET is_active = false WHERE type = 'TERMS'");
        jdbc.update("INSERT INTO legal_documents (type, version, effective_date, content, is_active) "
                + "VALUES ('TERMS', '1.0', CURRENT_DATE, 'Terms text', true)");

        // T-110: public, no Authorization header
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/legal/terms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("Terms text"));
            assertThat(redis.hasKey("legalDocument::TERMS")).isTrue();
        }
        assertExpiresWithin("legalDocument::TERMS", 3600);
    }

    @Test
    void privacyIsPublicToo() throws Exception {
        cacheManager.getCache(CacheNames.LEGAL_DOCUMENT_BY_TYPE).evict("PRIVACY");
        jdbc.update("UPDATE legal_documents SET is_active = false WHERE type = 'PRIVACY'");
        jdbc.update("INSERT INTO legal_documents (type, version, effective_date, content, is_active) "
                + "VALUES ('PRIVACY', '1.0', CURRENT_DATE, 'Privacy text', true)");
        mockMvc.perform(get("/api/v1/legal/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Privacy text"));
        // a stale token does not block a public page
        mockMvc.perform(get("/api/v1/legal/privacy").header("Authorization", "Bearer garbage")).andExpect(status().isOk());
    }

    @Test
    void onlyTheTwoLegalPagesArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/legal/other")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingDocumentIs404() throws Exception {
        cacheManager.getCache(CacheNames.LEGAL_DOCUMENT_BY_TYPE).evict("TERMS");
        Long active = jdbc.queryForObject("SELECT max(id) FROM legal_documents WHERE type = 'TERMS' AND is_active", Long.class);
        jdbc.update("UPDATE legal_documents SET is_active = false WHERE type = 'TERMS'");
        try {
            mockMvc.perform(get("/api/v1/legal/terms")).andExpect(status().isNotFound());
        } finally {
            // other tests sign up with the active documents
            jdbc.update("UPDATE legal_documents SET is_active = true WHERE id = ?", active);
            cacheManager.getCache(CacheNames.LEGAL_DOCUMENT_BY_TYPE).evict("TERMS");
        }
    }

    // T-085: cached entries expire with the configured TTL (-1 would mean none)
    private void assertExpiresWithin(String key, long maxSeconds) {
        assertThat(redis.getExpire(key)).isBetween(maxSeconds - 60, maxSeconds);
    }
}
