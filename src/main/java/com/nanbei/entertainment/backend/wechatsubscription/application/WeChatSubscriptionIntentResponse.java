package com.nanbei.entertainment.backend.wechatsubscription.application;

import java.time.Instant;
import java.util.UUID;

public record WeChatSubscriptionIntentResponse(
        UUID intentId,
        String templateId,
        int scene,
        String reserved,
        Instant expiresAt) {}
