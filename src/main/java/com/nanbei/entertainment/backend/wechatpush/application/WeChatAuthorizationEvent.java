package com.nanbei.entertainment.backend.wechatpush.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public record WeChatAuthorizationEvent(
        String eventType,
        long createTime,
        String appId,
        String openId,
        String unionId,
        String revokeInfo) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static WeChatAuthorizationEvent parse(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            String messageType = text(root, "MsgType");
            String eventType = text(root, "Event");
            String appId = text(root, "AppID");
            String openId = text(root, "OpenID");
            String unionId = text(root, "UnionID");
            if (!"event".equalsIgnoreCase(messageType)
                    || eventType.isEmpty()
                    || appId.isEmpty()
                    || (openId.isEmpty() && unionId.isEmpty())) {
                throw new IllegalArgumentException("invalid WeChat authorization event");
            }
            return new WeChatAuthorizationEvent(
                    eventType,
                    root.path("CreateTime").asLong(0L),
                    appId,
                    emptyToNull(openId),
                    emptyToNull(unionId),
                    emptyToNull(text(root, "RevokeInfo")));
        } catch (IOException error) {
            throw new IllegalArgumentException("invalid WeChat event JSON", error);
        }
    }

    public String fingerprintMaterial() {
        return String.join(
                "\n",
                appId,
                eventType,
                openId == null ? "" : openId,
                unionId == null ? "" : unionId,
                Long.toString(createTime),
                revokeInfo == null ? "" : revokeInfo);
    }

    private static String text(JsonNode root, String field) {
        return root.path(field).asText("").trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
