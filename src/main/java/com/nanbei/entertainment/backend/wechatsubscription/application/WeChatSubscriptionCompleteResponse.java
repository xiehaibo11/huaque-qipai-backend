package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;

public record WeChatSubscriptionCompleteResponse(
        WeChatSubscriptionGrantStatus status) {}
