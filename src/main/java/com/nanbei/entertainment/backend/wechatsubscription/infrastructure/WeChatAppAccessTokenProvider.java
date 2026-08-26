package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class WeChatAppAccessTokenProvider {
    private static final String BASE_URL = "https://api.weixin.qq.com";

    private final WeChatProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public WeChatAppAccessTokenProvider(
            WeChatProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                buildClient(properties),
                objectMapper,
                Clock.systemUTC());
    }

    WeChatAppAccessTokenProvider(
            WeChatProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String getToken() {
        CachedToken current = cachedToken;
        if (usable(current)) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            if (usable(current)) {
                return current.value();
            }
            cachedToken = fetch();
            return cachedToken.value();
        }
    }

    public synchronized void invalidate(String token) {
        if (cachedToken != null && cachedToken.value().equals(token)) {
            cachedToken = null;
        }
    }

    private CachedToken fetch() {
        if (!properties.isConfigured()) {
            throw new WeChatSubscriptionProviderException(
                    "WeChat application credentials are unavailable");
        }
        try {
            String body =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/cgi-bin/token")
                                                    .queryParam(
                                                            "grant_type",
                                                            "client_credential")
                                                    .queryParam(
                                                            "appid",
                                                            properties.appId())
                                                    .queryParam(
                                                            "secret",
                                                            properties.appSecret())
                                                    .build())
                            .accept(
                                    MediaType.APPLICATION_JSON,
                                    MediaType.TEXT_PLAIN)
                            .retrieve()
                            .body(String.class);
            return parse(body);
        } catch (WeChatSubscriptionProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new WeChatSubscriptionProviderException(
                    "WeChat application token request failed", exception);
        }
    }

    private CachedToken parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw invalidResponse();
            }
            int providerCode = root.path("errcode").asInt(0);
            String token = root.path("access_token").asText("");
            long expiresIn = root.path("expires_in").asLong(0);
            if (providerCode != 0 || token.isBlank() || expiresIn <= 0) {
                throw invalidResponse();
            }
            long refreshSkew = Math.min(300, Math.max(1, expiresIn / 10));
            Instant refreshAt =
                    clock.instant().plusSeconds(Math.max(1, expiresIn - refreshSkew));
            return new CachedToken(token, refreshAt);
        } catch (WeChatSubscriptionProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WeChatSubscriptionProviderException(
                    "WeChat application token response is invalid", exception);
        }
    }

    private boolean usable(CachedToken token) {
        return token != null && clock.instant().isBefore(token.refreshAt());
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

    private static WeChatSubscriptionProviderException invalidResponse() {
        return new WeChatSubscriptionProviderException(
                "WeChat application token response is invalid");
    }

    private record CachedToken(String value, Instant refreshAt) {}
}
