package com.example.paygenix.security;

import com.example.paygenix.config.OAuth2FailureHandler;
import com.example.paygenix.config.OAuth2SuccessHandler;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.PinRejectedException;
import com.example.paygenix.service.GoogleOidcUserService;
import com.example.paygenix.service.PinValidationService;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Decision #12, SEC-9, SEC-10: Google accounts by `sub`, verified email only, one-time code instead of tokens in the URL.
class GoogleSignInTest extends IntegrationTest {

    @Autowired GoogleOidcUserService googleOidcUserService;
    @Autowired OAuth2SuccessHandler successHandler;
    @Autowired OAuth2FailureHandler failureHandler;
    @Autowired PinValidationService pinValidationService;

    @Test
    void firstSignInCreatesAGoogleAccountWithoutPin() {
        String sub = UUID.randomUUID().toString();
        User user = googleOidcUserService.findOrCreate(google(sub, sub + "@gmail.test", true));

        assertThat(user.getProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo(sub);
        assertThat(user.getPinHash()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getVerificationStatus()).isEqualTo(User.VerificationStatus.PENDING);
    }

    @Test
    void laterSignInsFindTheAccountBySubEvenIfTheEmailChanged() {
        String sub = UUID.randomUUID().toString();
        User first = googleOidcUserService.findOrCreate(google(sub, sub + "@gmail.test", true));
        User again = googleOidcUserService.findOrCreate(google(sub, "renamed-" + sub + "@gmail.test", true));

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void unverifiedEmailIsRefused() {
        String sub = UUID.randomUUID().toString();
        assertThatThrownBy(() -> googleOidcUserService.findOrCreate(google(sub, sub + "@gmail.test", false)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(GoogleOidcUserService.EMAIL_NOT_VERIFIED);
    }

    @Test
    void emailOfAPasswordAccountIsNotTakenOver() {
        User local = createUser();
        assertThatThrownBy(() -> googleOidcUserService.findOrCreate(google(UUID.randomUUID().toString(), local.getEmail(), true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(GoogleOidcUserService.ACCOUNT_EXISTS);

        assertThat(userRepository.findById(local.getId()).orElseThrow().getProvider()).isEqualTo(User.AuthProvider.LOCAL);
    }

    @Test
    void redirectCarriesAOneTimeCodeThatIsExchangedOnce() throws Exception {
        String sub = UUID.randomUUID().toString();
        OidcUser oidcUser = google(sub, sub + "@gmail.test", true);
        googleOidcUserService.findOrCreate(oidcUser); // done by loadUser during the real login
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken(oidcUser, null));

        assertThat(session.isInvalid()).isTrue();

        String redirect = response.getRedirectedUrl();
        assertThat(redirect).startsWith("http://localhost:3000/oauth2/callback?code=").doesNotContain("token");
        String code = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("code");

        String body = exchange(code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(body, "$.data.token");
        assertThat(jwtService.extractUsername(access)).isEqualTo(sub + "@gmail.test");

        exchange(code).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_OAUTH_CODE"));
    }

    @Test
    void unknownCodeIsRejected() throws Exception {
        exchange("made-up-code").andExpect(status().isUnauthorized());
    }

    @Test
    void failureRedirectsToTheFrontendWithAnErrorCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error(GoogleOidcUserService.ACCOUNT_EXISTS)));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth2/callback?error=account_exists");

        MockHttpServletResponse other = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(new MockHttpServletRequest(), other,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "details <script>", null)));
        assertThat(other.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth2/callback?error=oauth_failed");
    }

    @Test
    void mixedCaseEmailOfAPasswordAccountIsAlsoRefused() {
        User local = createUser();
        assertThatThrownBy(() -> googleOidcUserService.findOrCreate(
                google(UUID.randomUUID().toString(), local.getEmail().toUpperCase(), true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void googleUserMustSetAPinBeforePaying() throws Exception {
        String sub = UUID.randomUUID().toString();
        User user = googleOidcUserService.findOrCreate(google(sub, sub + "@gmail.test", true));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> pinValidationService.validatePin(user.getId(), PIN)))
                .isInstanceOf(PinRejectedException.class)
                .extracting(e -> ((PinRejectedException) e).getErrorCode()).isEqualTo("PIN_NOT_SET");

        setPin(user, PIN).andExpect(status().isOk());
        transactionTemplate.executeWithoutResult(s -> pinValidationService.validatePin(user.getId(), PIN));
        setPin(user, "123456").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PIN_ALREADY_SET"));
    }

    @Test
    void firstPinMustBeSixDigits() throws Exception {
        String sub = UUID.randomUUID().toString();
        setPin(googleOidcUserService.findOrCreate(google(sub, sub + "@gmail.test", true)), "12ab").andExpect(status().isBadRequest());
    }

    private ResultActions exchange(String code) throws Exception {
        return mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions setPin(User user, String pin) throws Exception {
        return mockMvc.perform(post("/api/user/pin").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"" + pin + "\"}"));
    }

    private static OidcUser google(String sub, String email, boolean verified) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject(sub)
                .claim("email", email)
                .claim("email_verified", verified)
                .claim("name", "Google User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new DefaultOidcUser(List.of(), idToken);
    }
}
