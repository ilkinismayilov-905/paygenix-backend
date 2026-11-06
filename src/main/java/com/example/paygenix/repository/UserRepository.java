package com.example.paygenix.repository;

import com.example.paygenix.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    // case-insensitive (T-101); lower() matches the users_username_lower_key index
    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);

    // Serializes one user's PIN checks so parallel requests cannot exceed the attempt limit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(Long id);


    // pattern: a lower-case LIKE pattern with \ escaping (AdminService)
    @Query("SELECT u FROM User u WHERE lower(u.email) LIKE :pattern ESCAPE '\\' OR lower(u.username) LIKE :pattern ESCAPE '\\'")
    org.springframework.data.domain.Slice<User> search(@org.springframework.data.repository.query.Param("pattern") String pattern,
                                                        org.springframework.data.domain.Pageable pageable);
}
