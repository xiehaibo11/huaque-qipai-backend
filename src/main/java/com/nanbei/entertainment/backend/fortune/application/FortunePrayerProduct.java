package com.nanbei.entertainment.backend.fortune.application;

public record FortunePrayerProduct(
        String productCode,
        String name,
        long priceDiamonds,
        int wealthPoints,
        int luckPoints) {}
