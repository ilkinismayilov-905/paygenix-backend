package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuth2User(oAuth2User);
    }

    private OAuth2User processOAuth2User(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Əgər istifadəçi LOCAL idisə və indi Google ilə girirsə, provider-i yeniləyə bilərik
            if (user.getProvider() != User.AuthProvider.GOOGLE) {
                user.setProvider(User.AuthProvider.GOOGLE);
                userRepository.save(user);
            }
        } else {
            // Yeni Google istifadəçisi yarat
            user = User.builder()
                    .email(email)
                    .fullname(name)
                    .username(email) // Google istifadəçisi üçün email-i username edirik
                    .pinHash(UUID.randomUUID().toString()) // Google ilə girdiyi üçün parolu təsadüfi bir string edirik
                    .provider(User.AuthProvider.GOOGLE)
                    .roles(Set.of(Role.USER_ROLE)) // Standart rol
                    .status(User.Status.ACTIVE)
                    .verificationStatus(User.VerificationStatus.VERIFIED)
                    .build();
            userRepository.save(user);
            log.info("Yeni Google istifadəçisi qeydiyyatdan keçdi: {}", email);
        }

        return oAuth2User;
    }
}