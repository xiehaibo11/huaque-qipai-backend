package com.nanbei.entertainment.backend.auth.application;

public record TokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {
    public TokenPair(String accessToken, String refreshToken, long expiresIn) {
        this(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
