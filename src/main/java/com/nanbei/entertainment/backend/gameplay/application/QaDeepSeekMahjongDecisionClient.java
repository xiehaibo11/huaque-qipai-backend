package com.nanbei.entertainment.backend.gameplay.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class QaDeepSeekMahjongDecisionClient {
    private static final URI DEFAULT_BASE_URI = URI.create("https://api.deepseek.com");
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TOTAL_REQUEST_BUDGET = Duration.ofSeconds(5);
    private static final int MAX_REQUESTS = 3;

    private final boolean enabled;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long requestDeadlineNanos;
    private int requestCount;
    private boolean circuitOpen;

    public QaDeepSeekMahjongDecisionClient() {
        this(
                Boolean.parseBoolean(System.getenv("AI_MAHJONG_ENABLED")),
                configuredHttpClient(),
                configuredBaseUri(),
                System.getenv("AI_MAHJONG_API_KEY"),
                configuredModel(),
                configuredTimeout());
    }

    QaDeepSeekMahjongDecisionClient(
            boolean enabled,
            HttpClient httpClient,
            URI baseUri,
            String apiKey,
            String model,
            Duration timeout) {
        this.enabled = enabled;
        this.httpClient = httpClient;
        this.endpoint = baseUri.resolve("/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = boundedTimeout(timeout);
        this.requestDeadlineNanos = System.nanoTime() + TOTAL_REQUEST_BUDGET.toNanos();
    }

    public Optional<String> choose(String tableState, List<String> legalActionIds) {
        if (!enabled
                || isBlank(apiKey)
                || isBlank(tableState)
                || legalActionIds == null
                || legalActionIds.isEmpty()) {
            return Optional.empty();
        }
        Duration requestTimeout = nextRequestTimeout();
        if (requestTimeout == null) {
            return Optional.empty();
        }
        requestCount++;
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            HttpRequest.newBuilder(endpoint)
                                    .timeout(requestTimeout)
                                    .header("Authorization", "Bearer " + apiKey)
                                    .header("Content-Type", "application/json")
                                    .header("Accept", "application/json")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    requestBody(tableState, legalActionIds),
                                                    StandardCharsets.UTF_8))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200
                    || response.statusCode() >= 300
                    || isBlank(response.body())) {
                return failed();
            }
            Optional<String> choice = responseChoice(response.body());
            return choice.isPresent() ? choice : failed();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed();
        } catch (IOException | RuntimeException exception) {
            return failed();
        }
    }

    private Duration nextRequestTimeout() {
        if (circuitOpen || requestCount >= MAX_REQUESTS) {
            return null;
        }
        long remaining = requestDeadlineNanos - System.nanoTime();
        if (remaining <= 0L) {
            return null;
        }
        return Duration.ofNanos(Math.min(timeout.toNanos(), remaining));
    }

    private Optional<String> failed() {
        circuitOpen = true;
        return Optional.empty();
    }

    private String requestBody(String tableState, List<String> legalActionIds) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "model", model,
                            "max_tokens", 64,
                            "stream", false,
                            "thinking", Map.of("type", "disabled"),
                            "response_format", Map.of("type", "json_object"),
                            "messages",
                                    List.of(
                                            Map.of(
                                                    "role",
                                                    "system",
                                                    "content",
                                                    "你是台州麻将 AI。依据牌型、河牌、副露选择胜率最高动作；"
                                                            + "只能返回合法 actionId；JSON only."),
                                            Map.of(
                                                    "role",
                                                    "user",
                                                    "content",
                                                    "Table state:\n"
                                                            + tableState
                                                            + "\nLegal action IDs:\n"
                                                            + String.join("\n", legalActionIds)
                                                            + "\nReturn {\"actionId\":\"...\"}."))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Optional<String> responseChoice(String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                return Optional.empty();
            }
            JsonNode actionId = objectMapper.readTree(content.asText()).get("actionId");
            return actionId != null && actionId.isTextual() && !isBlank(actionId.asText())
                    ? Optional.of(actionId.asText())
                    : Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static HttpClient configuredHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(configuredTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static URI configuredBaseUri() {
        String value = System.getenv("AI_MAHJONG_BASE_URL");
        return isBlank(value) ? DEFAULT_BASE_URI : URI.create(value);
    }

    private static String configuredModel() {
        String value = System.getenv("AI_MAHJONG_MODEL");
        return isBlank(value) ? DEFAULT_MODEL : value;
    }

    private static Duration configuredTimeout() {
        String value = System.getenv("AI_MAHJONG_TIMEOUT");
        try {
            return isBlank(value) ? DEFAULT_TIMEOUT : boundedTimeout(Duration.parse(value));
        } catch (RuntimeException exception) {
            return DEFAULT_TIMEOUT;
        }
    }

    private static Duration boundedTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_TIMEOUT;
        }
        return timeout.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : timeout;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
