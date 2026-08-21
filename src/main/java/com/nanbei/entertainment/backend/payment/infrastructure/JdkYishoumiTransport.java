package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.common.config.YishoumiPaymentProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class JdkYishoumiTransport implements YishoumiTransport {
    private final HttpClient httpClient;
    private final Duration readTimeout;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdkYishoumiTransport(
            YishoumiPaymentProperties properties, ObjectMapper objectMapper) {
        this(
                properties.connectTimeout(),
                properties.readTimeout(),
                objectMapper);
    }

    JdkYishoumiTransport(
            Duration connectTimeout,
            Duration readTimeout,
            ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                readTimeout,
                objectMapper);
    }

    JdkYishoumiTransport(
            HttpClient httpClient,
            Duration readTimeout,
            ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.readTimeout = readTimeout;
        this.objectMapper = objectMapper;
    }

    @Override
    public String postJson(URI endpoint, Map<String, ?> fields) {
        HttpRequest request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(readTimeout)
                        .header(
                                "Content-Type",
                                "application/json; charset=UTF-8")
                        .header("Accept", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        encode(fields), StandardCharsets.UTF_8))
                        .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw upstreamFailure();
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstreamFailure();
        } catch (IOException exception) {
            throw upstreamFailure();
        }
    }

    private String encode(Map<String, ?> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception exception) {
            throw upstreamFailure();
        }
    }

    private static ApiException upstreamFailure() {
        return new ApiException(
                ErrorCode.PAYMENT_PROVIDER_UPSTREAM_FAILED,
                "支付渠道暂时不可用，请稍后重试");
    }
}
