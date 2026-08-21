package com.nanbei.entertainment.backend.auth.application;

public interface OneTapMobileExchange {
    VerifiedMobile exchange(String accessToken, String outId);
}
