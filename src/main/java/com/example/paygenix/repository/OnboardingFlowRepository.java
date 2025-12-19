package com.example.paygenix.repository;

import com.example.paygenix.entity.OnboardingFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnboardingFlowRepository extends JpaRepository<OnboardingFlow, String> {
}
