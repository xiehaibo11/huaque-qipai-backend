package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WeChatSubscriptionGrantInvalidator {
    private final WeChatSubscriptionGrantRepository repository;

    public WeChatSubscriptionGrantInvalidator(
            WeChatSubscriptionGrantRepository repository) {
        this.repository = repository;
    }

    public void invalidate(UUID userId) {
        repository.invalidateUnused(userId, Instant.now());
    }
}
