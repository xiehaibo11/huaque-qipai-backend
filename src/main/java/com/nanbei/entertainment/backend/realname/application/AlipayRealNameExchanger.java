package com.nanbei.entertainment.backend.realname.application;

public interface AlipayRealNameExchanger {
    AlipayRealName exchange(String authCode);
}
