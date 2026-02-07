package com.example.paygenix.repository;

import com.example.paygenix.entity.OnboardingChangePin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnboardingChangePinRepository extends JpaRepository<OnboardingChangePin, String> {
}
