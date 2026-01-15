package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oauth2User.getAttributes();
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email = null;
        String name = null;

        // Provider spesifik mapping (Google vs GitHub)
        if ("google".equalsIgnoreCase(registrationId)) {
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
        } else if ("github".equalsIgnoreCase(registrationId)) {
            // GitHub may not return email by default — ensure scope includes user:email or use /user/emails
            email = (String) attributes.get("email");
            if (email == null) {
                // fallback to login as username-like identifier
                email = ((String) attributes.get("login")) + "@github";
            }
            name = (String) attributes.get("name");
            if (name == null) name = (String) attributes.get("login");
        } else {
            // generic fallback
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
        }

        if (email == null) {
            // if no email, generate or throw depending on your policy
            email = registrationId + "_" + attributes.getOrDefault("id", "noid") + "@oauth";
        }

        // Find or create local user entity
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFullname(name != null ? name : email);
            userRepository.save(newUser);
        } else {
            // optionally update profile data from provider
            User existing = userOptional.get();
            boolean changed = false;
            if (existing.getFullname() == null && name != null) {
                existing.setFullname(name);
                changed = true;
            }
            if (changed) userRepository.save(existing);
        }

        // return oauth2User (we could wrap it in a custom OAuth2User implementation if needed)
        return oauth2User;
    }
}
