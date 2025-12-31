package com.example.paygenix.entity;

import com.example.paygenix.enums.Gender;
import com.example.paygenix.enums.MediaType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "edit_profile")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EditProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    @Email
    private String  email;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = true, length = 512)
    private String objectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 16)
    private MediaType mediaType;

    @CreationTimestamp
    private Instant createdAt;
}
