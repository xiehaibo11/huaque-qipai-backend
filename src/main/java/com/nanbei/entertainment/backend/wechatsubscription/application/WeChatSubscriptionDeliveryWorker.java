package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSendResult;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSender;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WeChatSubscriptionDeliveryWorker {
    private final WeChatSubscriptionDeliveryStateService stateService;
    private final WeChatSubscriptionIdentityService identityService;
    private final WeChatSubscriptionSender sender;
    private final CryptoService cryptoService;

    public WeChatSubscriptionDeliveryWorker(
            WeChatSubscriptionDeliveryStateService stateService,
            WeChatSubscriptionIdentityService identityService,
            WeChatSubscriptionSender sender,
            CryptoService cryptoService) {
        this.stateService = stateService;
        this.identityService = identityService;
        this.sender = sender;
        this.cryptoService = cryptoService;
    }

    public boolean processNext() {
        Optional<WeChatSubscriptionDeliveryWork> next = stateService.startNext();
        if (next.isEmpty()) {
            return false;
        }
        WeChatSubscriptionDeliveryWork work = next.get();
        try {
            WeChatSubscriptionIdentity identity =
                    identityService.requireCurrent(work.userId());
            if (!cryptoService.constantTimeEquals(
                    work.openIdSubjectHash(), identity.subjectHash())) {
                stateService.invalidate(work.deliveryId());
                return true;
            }
            WeChatSubscriptionSendResult result =
                    sender.send(work.message(identity.openId()));
            stateService.complete(work.deliveryId(), result);
        } catch (ApiException exception) {
            stateService.invalidate(work.deliveryId());
        }
        return true;
    }
}
