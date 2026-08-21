package com.nanbei.entertainment.backend.realname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "real_name_verifications")
public class RealNameVerificationEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "real_name_masked", nullable = false)
    private String realNameMasked;

    @Column(name = "id_card_hmac", nullable = false)
    private String idCardHmac;

    @Column(name = "id_card_masked", nullable = false)
    private String idCardMasked;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private RealNameSource source;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RealNameVerificationEntity() {}

    public RealNameVerificationEntity(
            UUID userId,
            String realNameMasked,
            String idCardHmac,
            String idCardMasked,
            LocalDate birthDate,
            RealNameSource source) {
        this.userId = userId;
        this.realNameMasked = realNameMasked;
        this.idCardHmac = idCardHmac;
        this.idCardMasked = idCardMasked;
        this.birthDate = birthDate;
        this.source = source;
    }

    @PrePersist
    void markVerified() {
        Instant now = Instant.now();
        verifiedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRealNameMasked() {
        return realNameMasked;
    }

    public String getIdCardHmac() {
        return idCardHmac;
    }

    public String getIdCardMasked() {
        return idCardMasked;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public RealNameSource getSource() {
        return source;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
