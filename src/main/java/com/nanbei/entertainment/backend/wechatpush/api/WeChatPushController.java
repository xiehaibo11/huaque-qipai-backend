package com.nanbei.entertainment.backend.wechatpush.api;

import com.nanbei.entertainment.backend.wechatpush.application.WeChatAuthorizationEvent;
import com.nanbei.entertainment.backend.wechatpush.application.WeChatAuthorizationEventService;
import com.nanbei.entertainment.backend.wechatpush.application.WeChatPushGateway;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/wechat/push")
public class WeChatPushController {
    private final WeChatPushGateway gateway;
    private final WeChatAuthorizationEventService eventService;

    public WeChatPushController(
            WeChatPushGateway gateway,
            WeChatAuthorizationEventService eventService) {
        this.gateway = gateway;
        this.eventService = eventService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> verify(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestParam(required = false) String echostr) {
        if (echostr == null
                || !gateway.verifyChallenge(signature, timestamp, nonce)) {
            return ResponseEntity.status(401).body("");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(echostr);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> receive(
            @RequestParam(name = "msg_signature", required = false)
                    String messageSignature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestBody String envelope) {
        try {
            WeChatAuthorizationEvent event =
                    gateway.decryptEvent(
                            envelope, messageSignature, timestamp, nonce);
            eventService.handle(event);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("success");
        } catch (IllegalArgumentException ignored) {
            return ResponseEntity.status(401).body("");
        }
    }
}
