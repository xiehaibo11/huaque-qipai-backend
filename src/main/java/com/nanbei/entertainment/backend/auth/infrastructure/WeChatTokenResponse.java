package com.nanbei.entertainment.backend.auth.infrastructure;

record WeChatTokenResponse(
        String openid,
        String unionid,
        Integer errcode,
        String errmsg,
        String accessToken,
        String nickname,
        byte[] avatarBytes,
        String avatarContentType) {
    WeChatTokenResponse(
            String openid,
            String unionid,
            Integer errcode,
            String errmsg) {
        this(openid, unionid, errcode, errmsg, null, null, null, null);
    }
}
