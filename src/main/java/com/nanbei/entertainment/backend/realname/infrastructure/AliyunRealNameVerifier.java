package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.common.config.RealNameProperties;
import com.nanbei.entertainment.backend.realname.application.RealNameVerifyResult;
import com.nanbei.entertainment.backend.realname.application.RealNameVerifier;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 阿里云市场身份二要素核验适配器。endpoint 为完整商品 URL，
 * 不同云市场商品的路径不同，全部通过配置注入。
 * 当前对接商品：cmapi00040094（GET {endpoint}?cardNo=..&amp;realName=..，
 * Header Authorization: APPCODE ..）。响应契约：
 * error_code 为 0 表示通讯成功，业务结果看 result.isok
 * （true=一致，false=不一致）；error_code 非 0 视为上游不可用。
 * 日志只记录 HTTP 状态码与判定布尔值，绝不记录姓名或证件号。
 */
@Component
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.realname",
        name = "enabled",
        havingValue = "true")
public class AliyunRealNameVerifier implements RealNameVerifier {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(AliyunRealNameVerifier.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RealNameProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AliyunRealNameVerifier(
            RealNameProperties properties, ObjectMapper objectMapper) {
        this(properties, buildClient(), objectMapper);
    }

    AliyunRealNameVerifier(
            RealNameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RealNameVerifyResult verify(
            String realName, String idCardNumber) {
        try {
            String responseBody =
                    restClient
                            .get()
                            .uri(
                                    UriComponentsBuilder.fromUriString(
                                                    properties.endpoint())
                                            .queryParam(
                                                    "realName", realName)
                                            .queryParam(
                                                    "cardNo", idCardNumber)
                                            .encode(
                                                    java.nio.charset
                                                            .StandardCharsets
                                                            .UTF_8)
                                            .build()
                                            .toUri())
                            .header(
                                    "Authorization",
                                    "APPCODE " + properties.appCode())
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    (request, clientResponse) -> {
                                        LOGGER.warn(
                                                "Real-name verification HTTP failure, status={}",
                                                clientResponse
                                                        .getStatusCode()
                                                        .value());
                                        throw new UpstreamUnavailableException();
                                    })
                            .body(String.class);
            return parseResult(responseBody);
        } catch (UpstreamUnavailableException exception) {
            return RealNameVerifyResult.UNAVAILABLE;
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "Real-name verification request failed, failureType={}",
                    exception.getClass().getSimpleName());
            return RealNameVerifyResult.UNAVAILABLE;
        }
    }

    private RealNameVerifyResult parseResult(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return RealNameVerifyResult.UNAVAILABLE;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorCode = root == null ? null : root.get("error_code");
            if (errorCode == null || !errorCode.isNumber()) {
                LOGGER.warn(
                        "Real-name verification returned unexpected payload");
                return RealNameVerifyResult.UNAVAILABLE;
            }
            if (errorCode.asInt() != 0) {
                LOGGER.warn(
                        "Real-name verification upstream error, errorCode={}",
                        errorCode.asInt());
                return RealNameVerifyResult.UNAVAILABLE;
            }
            JsonNode result = root.get("result");
            JsonNode isok = result == null ? null : result.get("isok");
            if (isok == null || !isok.isBoolean()) {
                LOGGER.warn(
                        "Real-name verification returned unexpected payload");
                return RealNameVerifyResult.UNAVAILABLE;
            }
            boolean matched = isok.asBoolean();
            // 上游判定成功走到这里就必须留痕：否则一次干净的 isok=false 不写任何日志，
            // 运维只能看到客户端的「姓名与身份证号不一致」，无法区分是上游判不一致，
            // 还是压根没走到上游（例如误挂了 local 桩）。只记布尔结果，不记姓名证件号。
            LOGGER.info("Real-name verification completed, matched={}", matched);
            return matched
                    ? RealNameVerifyResult.MATCH
                    : RealNameVerifyResult.MISMATCH;
        } catch (Exception exception) {
            LOGGER.warn("Real-name verification returned invalid JSON");
            return RealNameVerifyResult.UNAVAILABLE;
        }
    }

    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static final class UpstreamUnavailableException
            extends RuntimeException {}
}
