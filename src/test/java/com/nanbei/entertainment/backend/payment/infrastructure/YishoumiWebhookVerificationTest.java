package com.nanbei.entertainment.backend.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.config.YishoumiPaymentProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.application.PaymentProvider;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class YishoumiWebhookVerificationTest {
    private static final String APP_SECRET = "test-app-secret";
    private final YishoumiSigner signer = new YishoumiSigner();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesOfficialSdkJsonHashAndMapsAuthoritativeFields() {
        Map<String, String> fields = validFields();
        fields.put("future_extension", "extension-value");
        String rawBody = signedJson(fields);

        PaymentProvider.VerifiedPaymentCallback callback =
                provider().verifyWebhook(rawBody, "ignored-header-signature");

        assertThat(callback.eventId()).isEqualTo("YSM202608030001");
        assertThat(callback.merchantOrderNo()).isEqualTo("NB202608030000000000000000000001");
        assertThat(callback.providerOrderNo()).isEqualTo("YSM202608030001");
        assertThat(callback.amountMinor()).isEqualTo(26_800L);
        assertThat(callback.currency()).isEqualTo("CNY");
        assertThat(callback.paidAt())
                .isEqualTo(Instant.ofEpochSecond(1_785_787_200L));
    }

    @Test
    void rejectsTamperedSignatureAppIdAndNonSuccessState() {
        assertInvalid(
                fields -> fields.put("total_fee", "1"),
                true);
        assertInvalid(
                fields -> fields.put("appid", "another-app"),
                false);
        assertInvalid(
                fields -> fields.put("state", "NOTPAY"),
                false);
    }

    @Test
    void rejectsMissingIdentifiersAndInvalidNumbers() {
        assertInvalid(fields -> fields.remove("ysm_orderid"), false);
        assertInvalid(fields -> fields.remove("transaction_id"), false);
        assertInvalid(fields -> fields.put("total_fee", "0"), false);
        assertInvalid(fields -> fields.put("success_time", "not-a-time"), false);
    }

    @Test
    void rejectsDuplicateJsonKeys() {
        String signed = signedJson(validFields());
        String rawBody =
                signed.substring(0, signed.length() - 1)
                        + ",\"appid\":\"duplicate\"}";

        assertThatThrownBy(() -> provider().verifyWebhook(rawBody, null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ErrorCode.PAYMENT_CALLBACK_INVALID));
    }

    private void assertInvalid(
            Consumer<Map<String, String>> mutation,
            boolean mutateAfterSigning) {
        Map<String, String> fields = validFields();
        String body;
        if (mutateAfterSigning) {
            fields.put("hash", signer.sign(fields, APP_SECRET));
            mutation.accept(fields);
            body = json(fields);
        } else {
            mutation.accept(fields);
            body = signedJson(fields);
        }
        assertThatThrownBy(() -> provider().verifyWebhook(body, null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ErrorCode.PAYMENT_CALLBACK_INVALID));
    }

    private String signedJson(Map<String, String> source) {
        Map<String, String> fields = new LinkedHashMap<>(source);
        fields.put("hash", signer.sign(fields, APP_SECRET));
        return json(fields);
    }

    private String json(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private YishoumiPaymentProvider provider() {
        return new YishoumiPaymentProvider(
                properties(),
                (endpoint, fields) -> "{}",
                signer,
                new ObjectMapper(),
                Clock.fixed(
                        Instant.parse("2026-08-03T20:00:00Z"),
                        ZoneOffset.UTC),
                () -> "fixedNonce123");
    }

    private static Map<String, String> validFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mch_orderid", "NB202608030000000000000000000001");
        fields.put("total_fee", "26800");
        fields.put("transaction_id", "2026080322001000000000012345");
        fields.put("ysm_orderid", "YSM202608030001");
        fields.put("description", "365天会员");
        fields.put("state", "SUCCESS");
        fields.put("appid", "test-app-id");
        fields.put("success_time", "1785787200");
        fields.put("time", "1785787201");
        fields.put("nonce_str", "notifyNonce123");
        fields.put("attach", "00000000000000000000000000000204");
        return fields;
    }

    private static YishoumiPaymentProperties properties() {
        return new YishoumiPaymentProperties(
                true,
                "test-app-id",
                APP_SECRET,
                URI.create("https://www.yishoumi.cn/u/payment"),
                URI.create(
                        "https://api.nanbeiyule.com/api/v1/payments/webhooks/yishoumi"),
                URI.create("https://www.nanbeiyule.com/payment/result"),
                URI.create("https://www.nanbeiyule.com/payment/result"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(8));
    }
}
