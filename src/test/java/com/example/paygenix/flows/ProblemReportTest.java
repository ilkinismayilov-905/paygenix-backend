package com.example.paygenix.flows;

import com.example.paygenix.enums.Role;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemReportTest extends IntegrationTest {

    @Test
    void theReferenceIsStoredAndShownToAdmins() throws Exception {
        User user = createUser();
        mockMvc.perform(post("/api/reports").header("Authorization", bearer(user)).contentType("application/json")
                        .content("""
                                {"category":"INCORRECT_AMOUNT_CHARGED","message":" Charged twice ","referenceId":" card 1234 "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        assertThat(jdbc.queryForObject("SELECT reference_id FROM problem_reports WHERE user_id = ?", String.class, user.getId()))
                .isEqualTo("card 1234");
        mockMvc.perform(get("/api/reports").header("Authorization", bearer(createUser(Role.ADMIN_ROLE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == %d)].referenceId".formatted(user.getId())).value("card 1234"))
                .andExpect(jsonPath("$.data[?(@.userId == %d)].message".formatted(user.getId())).value("Charged twice"));
    }

    @Test
    void aBlankReferenceIsStoredAsNone() throws Exception {
        User user = createUser();
        mockMvc.perform(post("/api/reports").header("Authorization", bearer(user)).contentType("application/json")
                        .content("""
                                {"category":"SECURITY_CONCERN","message":"Odd sign-in","referenceId":"   "}"""))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT reference_id FROM problem_reports WHERE user_id = ?", String.class, user.getId()))
                .isNull();
    }
}
