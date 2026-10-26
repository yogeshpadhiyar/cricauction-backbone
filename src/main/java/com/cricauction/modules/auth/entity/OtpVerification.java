package com.cricauction.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "otp_verifications")
@IdClass(OtpVerificationId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerification {

    @Id
    @Column(name = "identifier", nullable = false, length = 150)
    private String identifier;

    @Id
    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "otp_code", nullable = false, length = 10)
    private String otpCode;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    @PreUpdate
    public void ensureIdentifier() {
        if (identifier == null || identifier.isBlank()) {
            if (email != null && !email.isBlank()) {
                identifier = email;
            } else if (mobile != null && !mobile.isBlank()) {
                identifier = mobile;
            }
        }
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
