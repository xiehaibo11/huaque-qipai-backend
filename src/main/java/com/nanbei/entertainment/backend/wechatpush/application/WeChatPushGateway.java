package com.nanbei.entertainment.backend.wechatpush.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.config.WeChatPushProperties;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class WeChatPushGateway {
    private static final int MAX_ENVELOPE_CHARACTERS = 65_536;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WeChatPushProperties pushProperties;
    private final WeChatProperties weChatProperties;

    public WeChatPushGateway(
            WeChatPushProperties pushProperties,
            WeChatProperties weChatProperties) {
        this.pushProperties = pushProperties;
        this.weChatProperties = weChatProperties;
    }

    public boolean isConfigured() {
        return pushProperties.isConfigured()
                && weChatProperties.appId() != null
                && !weChatProperties.appId().isBlank();
    }

    public boolean verifyChallenge(
            String signature, String timestamp, String nonce) {
        if (!isConfigured()) {
            return false;
        }
        try {
            return signature().matches(signature, timestamp, nonce);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public WeChatAuthorizationEvent decryptEvent(
            String envelope,
            String messageSignature,
            String timestamp,
            String nonce) {
        if (!isConfigured()
                || envelope == null
                || envelope.length() > MAX_ENVELOPE_CHARACTERS) {
            throw new IllegalArgumentException("invalid WeChat push envelope");
        }
        try {
            JsonNode root = JSON.readTree(envelope);
            String encrypted = root.path("Encrypt").asText("").trim();
            if (encrypted.isEmpty()
                    || !signature()
                            .matchesEncrypted(
                                    messageSignature,
                                    timestamp,
                                    nonce,
                                    encrypted)) {
                throw new IllegalArgumentException("invalid WeChat push signature");
            }
            String plaintext =
                    new WeChatPushCryptor(
                                    pushProperties.encodingAesKey(),
                                    weChatProperties.appId().trim())
                            .decrypt(encrypted);
            WeChatAuthorizationEvent event =
                    WeChatAuthorizationEvent.parse(plaintext);
            if (!weChatProperties.appId().trim().equals(event.appId())) {
                throw new IllegalArgumentException("WeChat event AppID does not match");
            }
            return event;
        } catch (IOException error) {
            throw new IllegalArgumentException("invalid WeChat push envelope", error);
        }
    }

    private WeChatPushSignature signature() {
        return new WeChatPushSignature(pushProperties.token());
    }
}
