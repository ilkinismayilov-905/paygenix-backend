package com.example.paygenix.service;

import com.example.paygenix.dto.response.OnboardingTokenResponse;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {
    private final OnboardingFlowRepository flowRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    public String submitEmail(String email) {
        if (userRepo.existsByEmail(email)) {
            throw new RuntimeException("User with email " + email + " already exists");
        }
        String otp = generateOtp();
        OnboardingFlow flow =OnboardingFlow.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .step(OnboardingFlow.Step.EMAIL_SUBMITTED)
                .otpHash(encoder.encode(otp))
                .otpExpiresAt(Instant.now().plusSeconds(300))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();


        OnboardingFlow f =flowRepo.save(flow);
        emailService.sendOtp(email,otp);
        return flow.getId();
    }

    public String verifyOtp(String flowId,String otp) {
        OnboardingFlow flow =getFlow(flowId, OnboardingFlow.Step.EMAIL_SUBMITTED);

        if (Instant.now().isAfter(flow.getOtpExpiresAt())) {
            throw new RuntimeException("OTP_EXPIRED");
        }

        if (!encoder.matches(otp, flow.getOtpHash())) {
            throw new RuntimeException("INVALID_OTP");
        }

        flow.setStep(OnboardingFlow.Step.OTP_VERIFIED);
        return jwtService.generateOnboardingToken(flow);
    }

    public void setFullName(String flowId,String fullName) {
        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.OTP_VERIFIED);
        log.info("BEFORE fullName = {}", flow.getFullName());
        flow.setFullName(fullName);
        log.info("AFTER fullName = {}", flow.getFullName());

        flow.setStep(OnboardingFlow.Step.FULLNAME_SET);
    }

    public void setUsername(String flowId,String username) {
        if(userRepo.existsByUsername(username)) {
            throw new RuntimeException("User with name " + username + " already exists");
        }

        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.FULLNAME_SET);
        flow.setUsernameCandidate(username);
        flow.setStep(OnboardingFlow.Step.USERNAME_SET);
    }

    public OnboardingTokenResponse setPin(String flowId, String pin) {
        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.USERNAME_SET);

        User user = User.builder()
                .email(flow.getEmail())
                .fullname(flow.getFullName())
                .username(flow.getUsernameCandidate())
                .pinHash(encoder.encode(pin))
                .status(User.Status.ACTIVE)
                .roles(new HashSet<>(Set.of("USER_ROLE")))
                .build();
        userRepo.save(user);

        flow.setStep(OnboardingFlow.Step.COMPLETED);
        return new OnboardingTokenResponse(jwtService.generateOnboardingUserToken(user));
    }


    private String generateOtp() {
        return String.valueOf(
                ThreadLocalRandom.current().nextInt(100000, 999999)
        );
    }


    private OnboardingFlow getFlow(String id, OnboardingFlow.Step step) {
        OnboardingFlow flow = flowRepo.findById(id)
                .orElseThrow();

        if (flow.getStep() != step)
            throw new RuntimeException("INVALID_STEP");

        return flow;
    }
}
