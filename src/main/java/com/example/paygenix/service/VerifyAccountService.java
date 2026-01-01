package com.example.paygenix.service;

import com.example.paygenix.dto.request.VerifyRequest;
import com.example.paygenix.entity.EditProfile;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.VerificationField;
import com.example.paygenix.exceptions.VerificationFailedException;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyAccountService {
    private final UserRepository userRepository;
    private final EditProfileRepository epRepository;

    public void verify(VerifyRequest verifyRequest,String email) {
        if (verifyRequest == null) {
            throw new IllegalArgumentException("Verify request cannot be null");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found by email: " + email));

        EditProfile ep = epRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("profile not found by email: " + email));

        List<VerificationField> mismatches = new ArrayList<>();

        if(!Objects.equals(verifyRequest.fullName(), ep.getFullName())) {
            mismatches.add(VerificationField.FULL_NAME);
        }
        if(!Objects.equals(verifyRequest.dateOfBirth(), ep.getDateOfBirth())) {
            mismatches.add(VerificationField.DATE_OF_BIRTH);
        }
        if(!Objects.equals(verifyRequest.gender(), ep.getGender())) {
            mismatches.add(VerificationField.GENDER);
        }

        if (!mismatches.isEmpty()) {
            throw new VerificationFailedException(mismatches);
        }

        user.setVerificationStatus(User.VerificationStatus.IDENTITY_VERIFIED);
        userRepository.save(user);

    }

    public void uploadVerificationImage(MultipartFile file,String email){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found by email: " + email));

        if(user.getVerificationStatus() != User.VerificationStatus.IDENTITY_VERIFIED) {
            throw new IllegalStateException("User cannot verified");
        }

        user.setVerificationStatus(User.VerificationStatus.VERIFIED);
        userRepository.save(user);

        System.out.println("User verification has been successfully completed");
    }
}
