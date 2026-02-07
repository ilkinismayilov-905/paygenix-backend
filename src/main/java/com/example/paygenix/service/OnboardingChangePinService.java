package com.example.paygenix.service;

import com.example.paygenix.entity.OnboardingChangePin;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.OnboardingChangePinRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardingChangePinService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final OnboardingChangePinRepository changePinRepository;

    @Transactional
    public String submitEmail(String email) {

        if (!userRepo.existsByEmail(email)) {
            throw new RuntimeException("USER_NOT_FOUND");
        }

        String otp = generateOtp();
        OnboardingChangePin flow =OnboardingChangePin.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .step(OnboardingChangePin.Step.OTP_SENT)
                .otpHash(encoder.encode(otp))
                .otpExpiresAt(Instant.now().plusSeconds(300))
                .build();


        OnboardingChangePin f =changePinRepository.save(flow);
        emailService.sendOtp(email,otp);
        log.info("Email sent");
        return flow.getId();
    }

    @Transactional
    public void verifyOtp(String flowId,String otp) {
        OnboardingChangePin flow =getFlow(flowId, OnboardingChangePin.Step.OTP_SENT);

        if (Instant.now().isAfter(flow.getOtpExpiresAt())) {
            throw new RuntimeException("OTP_EXPIRED");
        }

        if (!encoder.matches(otp, flow.getOtpHash())) {
            throw new RuntimeException("INVALID_OTP");
        }


        flow.setOtpHash(null);
        flow.setOtpExpiresAt(null);
        flow.setStep(OnboardingChangePin.Step.OTP_VERIFIED);
        log.info("OTP VERIFIED");
    }

    @Transactional
    public void changePin(String flowId,String newPin,String reEnterNewPin) {
        OnboardingChangePin flow =getFlow(flowId, OnboardingChangePin.Step.OTP_VERIFIED);
        User user = userRepo.findByEmail(flow.getEmail())
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if(newPin == null || reEnterNewPin == null){
            throw  new RuntimeException("NEW_PIN CANNOT BE NULL");
        }

        if(encoder.matches(newPin, user.getPinHash())) {
            throw new RuntimeException("New pin can not be same with old pin");
        }

        if (!newPin.equals(reEnterNewPin)){
            throw new RuntimeException("NEW_PIN DOES NOT MATCH");
        }


        user.setPinHash(encoder.encode(newPin));
        userRepo.save(user);
        flow.setStep(OnboardingChangePin.Step.COMPLETED);
        log.info("PIN CHANGED");
    }

    private String generateOtp() {
        return String.valueOf(
                ThreadLocalRandom.current().nextInt(100000, 999999)
        );
    }

    private OnboardingChangePin getFlow(String id, OnboardingChangePin.Step step) {
        OnboardingChangePin flow = changePinRepository.findById(id)
                .orElseThrow();

        if (flow.getStep() != step)
            throw new RuntimeException("INVALID_STEP");

        return flow;
    }

}
