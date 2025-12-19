package com.example.paygenix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "onboarding_flows")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OnboardingFlow {
    @Id
    private String id;

    @Column(nullable = false)
    private String email;

    @Enumerated(value = EnumType.STRING)
    private Step step;

    private String otpHash;
    private Instant otpExpiresAt;

    private String fullName;
    private String usernameCandidate;

    private Instant expiresAt;

    @Version
    private long version;

    public enum Step {
        EMAIL_SUBMITTED,
        OTP_VERIFIED,
        FULLNAME_SET,
        USERNAME_SET,
        COMPLETED
    }
}
