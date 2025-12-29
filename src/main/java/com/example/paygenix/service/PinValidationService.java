package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PinValidationService {

    private final UserRepository userRepository;
    private final PasswordEncoder pinEncoder;

    public void validatePin(Long userId, String rawPin) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean matches = pinEncoder.matches(rawPin, user.getPinHash());

        if (!matches) {
            throw new NotFoundException("Invalid PIN");
        }
    }
}