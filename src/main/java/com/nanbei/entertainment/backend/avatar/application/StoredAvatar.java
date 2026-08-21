package com.nanbei.entertainment.backend.avatar.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StoredAvatar(
        String avatarKey,
        UUID userId,
        byte[] bytes,
        String contentType,
        String sha256,
        int width,
        int height,
        Instant updatedAt) {
    public StoredAvatar {
        avatarKey = Objects.requireNonNull(avatarKey, "avatarKey");
        userId = Objects.requireNonNull(userId, "userId");
        bytes = Objects.requireNonNull(bytes, "bytes").clone();
        contentType = Objects.requireNonNull(contentType, "contentType");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
