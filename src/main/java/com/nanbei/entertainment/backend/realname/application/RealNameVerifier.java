package com.nanbei.entertainment.backend.realname.application;

public interface RealNameVerifier {
    RealNameVerifyResult verify(String realName, String idCardNumber);
}
