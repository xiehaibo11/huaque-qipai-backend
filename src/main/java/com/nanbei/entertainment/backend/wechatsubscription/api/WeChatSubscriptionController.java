package com.nanbei.entertainment.backend.wechatsubscription.api;

import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionCompleteResponse;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionCompletion;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionIntentResponse;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionIntentService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wechat/subscriptions/intents")
public class WeChatSubscriptionController {
    private final WeChatSubscriptionIntentService service;

    public WeChatSubscriptionController(WeChatSubscriptionIntentService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<WeChatSubscriptionIntentResponse> create(
            @AuthenticationPrincipal Jwt jwt) {
        WeChatSubscriptionIntentResponse response = service.create(userId(jwt));
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/wechat/subscriptions/intents/"
                                        + response.intentId()))
                .body(response);
    }

    @PostMapping("/{intentId}/complete")
    WeChatSubscriptionCompleteResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID intentId,
            @RequestBody CompletionRequest request) {
        return service.complete(userId(jwt), intentId, request.toCommand());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record CompletionRequest(
            int errCode,
            String action,
            String templateId,
            int scene,
            String reserved,
            String openId,
            String transaction) {
        WeChatSubscriptionCompletion toCommand() {
            return new WeChatSubscriptionCompletion(
                    errCode,
                    action,
                    templateId,
                    scene,
                    reserved,
                    openId,
                    transaction);
        }
    }
}
