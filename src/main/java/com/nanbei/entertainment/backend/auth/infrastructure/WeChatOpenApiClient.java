package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!local")
public class WeChatOpenApiClient implements WeChatCodeExchange {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(WeChatOpenApiClient.class);
    private static final String BASE_URL = "https://api.weixin.qq.com";
    private static final String ACCESS_TOKEN_PATH =
            "/sns/oauth2/access_token";
    private static final String USER_INFO_PATH = "/sns/userinfo";
    private static final Duration DEFAULT_CONNECT_TIMEOUT =
            Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String INVALID_CREDENTIAL_MESSAGE =
            "微信授权凭证无效或已过期";
    private static final String UPSTREAM_ERROR_MESSAGE =
            "微信登录服务暂不可用，请稍后重试";

    private final WeChatProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WeChatOpenApiClient(
            WeChatProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                buildClient(properties, RestClient.builder()),
                objectMapper);
    }

    WeChatOpenApiClient(
            WeChatProperties properties, RestClient restClient) {
        this(properties, restClient, new ObjectMapper());
    }

    WeChatOpenApiClient(
            WeChatProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeChatTokenResponse exchange(String code) {
        if (code == null || code.isBlank()) {
            throw invalidCredential();
        }
        try {
            String responseBody =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(ACCESS_TOKEN_PATH)
                                                    .queryParam(
                                                            "appid",
                                                            properties.appId())
                                                    .queryParam(
                                                            "secret",
                                                            properties.appSecret())
                                                    .queryParam("code", code)
                                                    .queryParam(
                                                            "grant_type",
                                                            "authorization_code")
                                                    .build())
                            .accept(
                                    MediaType.APPLICATION_JSON,
                                    MediaType.TEXT_PLAIN)
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    (request, clientResponse) -> {
                                        LOGGER.warn(
                                                "WeChat OAuth token exchange HTTP failure, status={}",
                                                clientResponse
                                                        .getStatusCode()
                                                        .value());
                                        throw upstreamFailure();
                                    })
                            .body(String.class);
            WeChatTokenResponse tokenResponse =
                    validateResponse(parseTokenResponse(responseBody));
            return loadUserProfile(tokenResponse);
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "WeChat OAuth token exchange request failed, failureType={}",
                    exception.getClass().getSimpleName());
            throw upstreamFailure();
        }
    }

    private WeChatTokenResponse parseTokenResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw upstreamFailure();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.isObject()) {
                throw upstreamFailure();
            }
            return new WeChatTokenResponse(
                    textOrNull(root.get("openid")),
                    textOrNull(root.get("unionid")),
                    integerOrNull(root.get("errcode")),
                    textOrNull(root.get("errmsg")),
                    textOrNull(root.get("access_token")),
                    null,
                    null,
                    null);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("WeChat OAuth token exchange returned invalid JSON");
            throw upstreamFailure();
        }
    }

    private WeChatTokenResponse loadUserProfile(
            WeChatTokenResponse tokenResponse) {
        byte[] responseBody =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path(USER_INFO_PATH)
                                                .queryParam(
                                                        "access_token",
                                                        tokenResponse.accessToken())
                                                .queryParam(
                                                        "openid",
                                                        tokenResponse.openid())
                                                .queryParam("lang", "zh_CN")
                                                .build())
                        .accept(
                                MediaType.APPLICATION_JSON,
                                MediaType.TEXT_PLAIN)
                        .retrieve()
                        .onStatus(
                                status -> status.isError(),
                                (request, clientResponse) -> {
                                    LOGGER.warn(
                                            "WeChat user info HTTP failure, status={}",
                                            clientResponse
                                                    .getStatusCode()
                                                    .value());
                                    throw upstreamFailure();
                                })
                        .body(byte[].class);
        WeChatProfile profile =
                parseProfile(
                        responseBody == null
                                ? null
                                : new String(
                                        responseBody,
                                        StandardCharsets.UTF_8));
        AvatarDownload avatar = downloadAvatar(profile.avatarUrl());
        return new WeChatTokenResponse(
                tokenResponse.openid(),
                firstNonBlank(tokenResponse.unionid(), profile.unionId()),
                null,
                null,
                tokenResponse.accessToken(),
                profile.nickname(),
                avatar.bytes(),
                avatar.contentType());
    }

    private WeChatProfile parseProfile(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw upstreamFailure();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.isObject()) {
                throw upstreamFailure();
            }
            Integer providerCode = integerOrNull(root.get("errcode"));
            if (providerCode != null && providerCode != 0) {
                LOGGER.warn(
                        "WeChat user info rejected, errcode={}",
                        providerCode);
                throw upstreamFailure();
            }
            return new WeChatProfile(
                    textOrNull(root.get("nickname")),
                    textOrNull(root.get("headimgurl")),
                    textOrNull(root.get("unionid")));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("WeChat user info returned invalid JSON");
            throw upstreamFailure();
        }
    }

    private AvatarDownload downloadAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return new AvatarDownload(null, null);
        }
        ResponseEntity<byte[]> response =
                restClient
                        .get()
                        .uri(avatarUrl)
                        .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG)
                        .retrieve()
                        .onStatus(
                                status -> status.isError(),
                                (request, clientResponse) -> {
                                    LOGGER.warn(
                                            "WeChat avatar download HTTP failure, status={}",
                                            clientResponse
                                                    .getStatusCode()
                                                    .value());
                                    throw upstreamFailure();
                                })
                        .toEntity(byte[].class);
        MediaType contentType = response.getHeaders().getContentType();
        return new AvatarDownload(
                response.getBody(),
                contentType == null ? null : contentType.toString());
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.stringValue();
    }

    private static Integer integerOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private WeChatTokenResponse validateResponse(
            WeChatTokenResponse response) {
        if (response == null) {
            throw upstreamFailure();
        }
        Integer providerCode = response.errcode();
        if (providerCode != null && providerCode != 0) {
            LOGGER.warn(
                    "WeChat OAuth token exchange rejected, errcode={}",
                    providerCode);
            if (providerCode == 40029
                    || providerCode == 40163
                    || providerCode == 41008) {
                throw invalidCredential();
            }
            throw upstreamFailure();
        }
        if (response.openid() == null || response.openid().isBlank()) {
            throw upstreamFailure();
        }
        if (response.accessToken() == null
                || response.accessToken().isBlank()) {
            throw upstreamFailure();
        }
        return response;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static RestClient buildClient(
            WeChatProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
                positiveOrDefault(
                        properties.connectTimeout(), DEFAULT_CONNECT_TIMEOUT));
        requestFactory.setReadTimeout(
                positiveOrDefault(
                        properties.readTimeout(), DEFAULT_READ_TIMEOUT));
        return builder
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    private static Duration positiveOrDefault(
            Duration candidate, Duration fallback) {
        return candidate == null
                        || candidate.isZero()
                        || candidate.isNegative()
                ? fallback
                : candidate;
    }

    private static ApiException invalidCredential() {
        return new ApiException(
                ErrorCode.AUTH_INVALID_CREDENTIAL,
                INVALID_CREDENTIAL_MESSAGE);
    }

    private static ApiException upstreamFailure() {
        return new ApiException(
                ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED,
                UPSTREAM_ERROR_MESSAGE);
    }

    private record WeChatProfile(
            String nickname, String avatarUrl, String unionId) {}

    private record AvatarDownload(byte[] bytes, String contentType) {}
}
