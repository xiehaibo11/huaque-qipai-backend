package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

final class YishoumiJsonCodec {
    private static final int MAX_PAYLOAD_LENGTH = 64 * 1024;

    private final ObjectMapper objectMapper;

    YishoumiJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, String> decode(String rawBody) {
        if (rawBody == null
                || rawBody.isBlank()
                || rawBody.length() > MAX_PAYLOAD_LENGTH) {
            throw invalidJson();
        }
        try (JsonParser parser = objectMapper.createParser(rawBody)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw invalidJson();
            }
            Map<String, String> result = new LinkedHashMap<>();
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.PROPERTY_NAME) {
                    throw invalidJson();
                }
                String key = parser.currentName();
                String value = scalarValue(parser, parser.nextToken());
                if (key == null
                        || key.isBlank()
                        || result.putIfAbsent(key, value) != null) {
                    throw invalidJson();
                }
            }
            if (parser.nextToken() != null) {
                throw invalidJson();
            }
            return result;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidJson();
        }
    }

    private static String scalarValue(JsonParser parser, JsonToken token) {
        if (token == JsonToken.VALUE_STRING
                || token == JsonToken.VALUE_NUMBER_INT
                || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getValueAsString();
        }
        if (token == JsonToken.VALUE_NULL) {
            return "";
        }
        throw invalidJson();
    }

    private static ApiException invalidJson() {
        return new ApiException(
                ErrorCode.PAYMENT_CALLBACK_INVALID,
                "支付通知 JSON 无效");
    }
}
