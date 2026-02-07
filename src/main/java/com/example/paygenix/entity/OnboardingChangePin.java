package com.example.paygenix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "onboarding_change_pin")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OnboardingChangePin {

    @Id
    private String id;

    @Column(nullable = false)
    private String email;

    @Enumerated(value = EnumType.STRING)
    private Step step;

    private String otpHash;

    private Instant otpExpiresAt;

    @Version
    private long version;

    @CreationTimestamp
    private Instant createdAt;

    public enum Step {
        OTP_SENT,
        OTP_VERIFIED,
        COMPLETED
    }
}
