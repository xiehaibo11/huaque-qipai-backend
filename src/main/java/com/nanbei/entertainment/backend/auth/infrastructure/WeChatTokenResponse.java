package com.nanbei.entertainment.backend.auth.infrastructure;

record WeChatTokenResponse(
        String openid, String unionid, Integer errcode, String errmsg) {
}
