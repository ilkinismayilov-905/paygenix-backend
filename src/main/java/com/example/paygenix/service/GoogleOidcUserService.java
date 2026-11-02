package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Google sign-in (decision #12, SEC-10). The scope includes `openid`, so Spring calls this OIDC service, not a
 * plain OAuth2 user service. Accounts are matched by Google's `sub`, never by email alone; the first sign-in with
 * a verified email creates the account. An email that already belongs to a password account is refused rather
 * than linked: linking would need the owner to prove they hold that account first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOidcUserService extends OidcUserService {

    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";
    public static final String ACCOUNT_EXISTS = "account_exists";
    public static final String ACCOUNT_LOCKED = "account_locked";

    private final UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        findOrCreate(oidcUser);
        return oidcUser;
    }

    public User findOrCreate(OidcUser oidcUser) {
        User user = userRepository.findByProviderAndProviderId(User.AuthProvider.GOOGLE, oidcUser.getSubject())
                .orElseGet(() -> create(oidcUser));
        // refuse before a one-time code is issued, so the frontend gets ?error=account_locked (T-102)
        if (!user.isAccountNonLocked()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ACCOUNT_LOCKED));
        }
        return user;
    }

    private User create(OidcUser oidcUser) {
        if (oidcUser.getEmail() == null || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(EMAIL_NOT_VERIFIED));
        }
        // onboarding stores emails lowercased; the account_exists check must match them
        String email = oidcUser.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email) || userRepository.existsByUsername(email)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ACCOUNT_EXISTS));
        }
        User user;
        try {
            user = userRepository.save(User.builder()
                    .email(email)
                    .fullname(oidcUser.getFullName() != null ? oidcUser.getFullName() : email)
                    .username(email)
                    .provider(User.AuthProvider.GOOGLE)
                    .providerId(oidcUser.getSubject())
                    .roles(Set.of(Role.USER_ROLE))
                    .status(User.Status.ACTIVE)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // a parallel first sign-in with the same sub or email won the insert
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth_failed"));
        }
        log.info("Google account created, user {}", user.getId());
        return user;
    }
}
