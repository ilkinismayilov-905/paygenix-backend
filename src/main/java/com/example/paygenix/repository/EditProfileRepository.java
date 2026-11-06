package com.example.paygenix.repository;

import com.example.paygenix.entity.EditProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EditProfileRepository extends JpaRepository<EditProfile, Long> {
    Optional<EditProfile> findByEmail(String email);
    Optional<EditProfile> findByUserId(Long userId);

    // photo changes hold the row, so two uploads (or an upload and a remove) cannot lose each other's file
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM EditProfile p WHERE p.user.id = :userId")
    Optional<EditProfile> findByUserIdForUpdate(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<EditProfile> findByUserIdIn(java.util.Collection<Long> userIds);
}
