package com.example.paygenix.entity;

import com.example.paygenix.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

// Dynamic: an UPDATE writes only the columns that changed, so two flows saving the same user (a KYC approval and an
// email change, say) do not undo each other's column with a stale copy.
@DynamicUpdate
@Entity
@Data
@ToString(onlyExplicitlyIncluded = true) // Spring Security logs the principal; keep email and password hash out
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "username")
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User implements UserDetails {

    // T-080: emails are stored and looked up lower-case; Locale.ROOT so "I" never becomes a dotless "ı" (az/tr locales)
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String fullname;

    // null: Google account that has not set its PIN yet
    private String pinHash;

    // null: no password login (pre-V9 test users, Google accounts)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    // the provider's stable subject id (Google `sub`); null for LOCAL
    private String providerId;

    // VERIFIED once an admin approved an identity document (KycService)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$")
    String phoneNumber;

    @CreationTimestamp
    private LocalDateTime createdTime;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Transient
    private long expiresIn;

    // Identity is the database id; getId() (not the field) so Hibernate proxies compare correctly.
    // shortcut: unsaved users (id null) are never equal and hash alike, so do not keep them in sets before persist
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof User other && getId() != null && getId().equals(other.getId()));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @PrePersist
    void prePersist() {
        if (verificationStatus == null) {
            verificationStatus = VerificationStatus.UNVERIFIED;
        }
        if (provider == null) {
            provider = AuthProvider.LOCAL;
        }
    }

    public enum Status {
        ACTIVE,
        LOCKED
    }

    public enum VerificationStatus {
        UNVERIFIED,
        VERIFIED
    }
    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    /** The login name for Spring Security and the token subject: the email. The chosen @username is getPublicUsername(). */
    @Override
    public String getUsername() {
        return email;
    }

    /** The @username the user picked at sign-up (the email for Google accounts); what other people and admins see. */
    public String getPublicUsername() {
        return username;
    }

    public String getDisplayName() {
        return fullname;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != Status.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }

}
