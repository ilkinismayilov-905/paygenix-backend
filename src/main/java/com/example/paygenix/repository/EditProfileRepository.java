package com.example.paygenix.repository;

import com.example.paygenix.entity.EditProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EditProfileRepository extends JpaRepository<EditProfile, Long> {
    Optional<EditProfile> findByEmail(String email);
    Optional<EditProfile> findByUserId(Long userId);
}
