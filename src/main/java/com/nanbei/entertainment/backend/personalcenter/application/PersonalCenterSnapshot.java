package com.nanbei.entertainment.backend.personalcenter.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonalCenterSnapshot(
        Player player,
        Wallet wallet,
        Account account,
        Region region,
        HealthCertification healthCertification,
        Membership membership,
        Capabilities capabilities,
        PersonalCenterPrivacySettings privacy) {
    public record Player(
            UUID userId,
            long publicPlayerId,
            String displayName,
            String avatarKey,
            int membershipLevel) {}

    public record Wallet(
            long purchasedRoomCards,
            long boundRoomCards,
            long coins,
            long diamonds) {}

    public record Account(
            boolean phoneBound,
            String maskedPhone,
            List<String> identityProviders) {
        public Account {
            maskedPhone = maskedPhone == null ? "" : maskedPhone;
            identityProviders = List.copyOf(identityProviders);
        }
    }

    public record Region(long lobbyId, String areaName) {}

    public record HealthCertification(
            String status,
            String realNameMasked,
            String idCardMasked,
            boolean alipayOneTapEnabled) {}

    public record Membership(
            boolean active,
            int level,
            Instant expiresAt,
            boolean autoRenew,
            long remainingDays) {}

    public record Capabilities(
            boolean avatarRefresh,
            boolean regionSwitch,
            boolean accountSwitch,
            boolean accountDeletion,
            boolean phoneRebind,
            boolean healthCertification) {}
}
