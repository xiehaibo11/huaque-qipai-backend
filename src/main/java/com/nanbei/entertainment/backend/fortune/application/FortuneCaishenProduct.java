package com.nanbei.entertainment.backend.fortune.application;

public record FortuneCaishenProduct(
        String productCode,
        String name,
        long priceDiamonds,
        long durationSeconds) {}
