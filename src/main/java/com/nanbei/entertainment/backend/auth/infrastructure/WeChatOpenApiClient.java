package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
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
            return validateResponse(parseResponse(responseBody));
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "WeChat OAuth token exchange request failed, failureType={}",
                    exception.getClass().getSimpleName());
            throw upstreamFailure();
        }
    }

    private WeChatTokenResponse parseResponse(String responseBody) {
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
                    textOrNull(root.get("errmsg")));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("WeChat OAuth token exchange returned invalid JSON");
            throw upstreamFailure();
        }
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
        return response;
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
}
