package com.nanbei.entertainment.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 200)
    private String providerSubject;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserIdentityEntity() {}

    public UserIdentityEntity(
            UserEntity user,
            IdentityProvider provider,
            String providerSubject,
            String phoneNumber) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.phoneNumber = phoneNumber;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UserEntity getUser() {
        return user;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void linkTo(UserEntity user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        this.user = user;
    }

    public void rebindPhone(String phoneNumber) {
        if (provider != IdentityProvider.PHONE
                || phoneNumber == null
                || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phone identity required");
        }
        providerSubject = phoneNumber;
        this.phoneNumber = phoneNumber;
    }
}
