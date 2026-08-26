package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class WeChatSubscriptionSender {
    private static final String BASE_URL = "https://api.weixin.qq.com";
    private static final String SEND_PATH = "/cgi-bin/message/template/subscribe";
    private static final Set<Integer> TOKEN_INVALID_CODES =
            Set.of(40001, 40014, 42001);

    private final WeChatAppAccessTokenProvider tokenProvider;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WeChatSubscriptionSender(
            WeChatProperties properties,
            WeChatAppAccessTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        this(properties, tokenProvider, buildClient(properties), objectMapper);
    }

    WeChatSubscriptionSender(
            WeChatProperties properties,
            WeChatAppAccessTokenProvider tokenProvider,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public WeChatSubscriptionSendResult send(WeChatSubscriptionMessage message) {
        try {
            String token = tokenProvider.getToken();
            return send(message, token, true);
        } catch (WeChatSubscriptionProviderException exception) {
            return WeChatSubscriptionSendResult.retryable("TOKEN_UNAVAILABLE");
        }
    }

    private WeChatSubscriptionSendResult send(
            WeChatSubscriptionMessage message,
            String token,
            boolean allowTokenRefresh) {
        try {
            String body =
                    restClient
                            .post()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(SEND_PATH)
                                                    .queryParam("access_token", token)
                                                    .build())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(
                                    MediaType.APPLICATION_JSON,
                                    MediaType.TEXT_PLAIN)
                            .body(payload(message))
                            .retrieve()
                            .body(String.class);
            Integer providerCode = providerCode(body);
            if (providerCode == null) {
                return WeChatSubscriptionSendResult.terminal(
                        null, "INVALID_RESPONSE");
            }
            if (providerCode == 0) {
                return WeChatSubscriptionSendResult.sent();
            }
            if (allowTokenRefresh && TOKEN_INVALID_CODES.contains(providerCode)) {
                tokenProvider.invalidate(token);
                try {
                    return send(message, tokenProvider.getToken(), false);
                } catch (WeChatSubscriptionProviderException exception) {
                    return WeChatSubscriptionSendResult.retryable(
                            "TOKEN_UNAVAILABLE");
                }
            }
            return WeChatSubscriptionSendResult.terminal(
                    providerCode,
                    TOKEN_INVALID_CODES.contains(providerCode)
                            ? "TOKEN_REJECTED_AFTER_REFRESH"
                            : "PROVIDER_REJECTED");
        } catch (RestClientResponseException exception) {
            return WeChatSubscriptionSendResult.ambiguous();
        } catch (RestClientException exception) {
            return WeChatSubscriptionSendResult.ambiguous();
        }
    }

    private String payload(WeChatSubscriptionMessage message) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("touser", message.openId());
            root.put("template_id", message.templateId());
            root.put("scene", message.scene());
            root.put("title", message.title());
            if (message.url() != null && !message.url().isBlank()) {
                root.put("url", message.url());
            }
            ObjectNode content = root.putObject("data").putObject("content");
            content.put("value", message.content());
            content.put("color", "#173177");
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalArgumentException("subscription message is invalid", exception);
        }
    }

    private Integer providerCode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode code = root == null ? null : root.get("errcode");
            return code == null || !code.isNumber() ? null : code.asInt();
        } catch (Exception exception) {
            return null;
        }
    }

    private static RestClient buildClient(WeChatProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }
}
