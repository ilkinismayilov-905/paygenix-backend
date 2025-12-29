package com.example.paygenix.repository;

import com.example.paygenix.entity.PaymentOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOnboardingRepository extends JpaRepository<PaymentOnboarding, String> {
}
