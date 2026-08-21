package com.nanbei.entertainment.backend.avatar.application;

import java.util.Objects;

public record NormalizedAvatar(
        byte[] bytes,
        String contentType,
        String sha256,
        int width,
        int height) {
    public NormalizedAvatar {
        bytes = Objects.requireNonNull(bytes, "bytes").clone();
        contentType = Objects.requireNonNull(contentType, "contentType");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        if (bytes.length == 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Avatar data and dimensions must be positive");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
