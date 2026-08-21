package com.nanbei.entertainment.backend.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.config.YishoumiPaymentProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.application.PaymentProvider;
import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class YishoumiPaymentProviderTest {
    private static final String APP_SECRET = "test-app-secret";
    private static final Instant NOW = Instant.parse("2026-08-03T20:00:00Z");
    private final YishoumiSigner signer = new YishoumiSigner();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsFixedAlipayH5PaymentFromServerProduct() {
        CapturingTransport transport =
                new CapturingTransport(this::successfulResponse);
        PaymentProductEntity product = product();
        PaymentOrderEntity order = order(product);

        PaymentProvider.PaymentCreation creation =
                provider(properties(true), transport)
                        .createPayment(order, product);

        assertThat(transport.endpoint)
                .isEqualTo(URI.create("https://www.yishoumi.cn/u/payment"));
        assertThat(transport.fields)
                .containsEntry("appid", "test-app-id")
                .containsEntry("description", "365天会员")
                .containsEntry("nonce_str", "fixedNonce123")
                .containsEntry("mch_orderid", order.getMerchantOrderNo())
                .containsEntry(
                        "attach", order.getId().toString().replace("-", ""));
        assertThat(transport.fields.get("total")).isEqualTo(26_800L);
        assertThat(transport.fields.get("payType")).isEqualTo(11);
        assertThat(transport.fields.get("time")).isEqualTo(1_785_787_200L);
        assertThat(transport.fields.get("notify_url"))
                .isEqualTo(
                        "https://api.nanbeiyule.com/api/v1/payments/webhooks/yishoumi");
        assertThat(transport.fields.get("callback_url"))
                .isEqualTo(
                        "https://www.nanbeiyule.com/payment/result?orderId="
                                + order.getId()
                                + "&outcome=success");
        assertThat(transport.fields.get("nopay_url"))
                .isEqualTo(
                        "https://www.nanbeiyule.com/payment/result?orderId="
                                + order.getId()
                                + "&outcome=cancel");
        assertThat(
                        signer.verify(
                                transport.fields,
                                APP_SECRET,
                                transport.fields.get("sign").toString()))
                .isTrue();
        assertThat(creation.providerOrderNo()).isNull();
        assertThat(creation.parameters())
                .containsEntry("paymentUrl", "https://pay.example/alipay/order")
                .containsEntry("payType", "11");
    }

    @Test
    void acceptsUpstreamEchoOfOriginalRequestSignature() {
        CapturingTransport transport =
                new CapturingTransport(this::requestSignatureEchoResponse);
        PaymentProductEntity product = product();

        PaymentProvider.PaymentCreation creation =
                provider(properties(true), transport)
                        .createPayment(order(product), product);

        assertThat(creation.parameters())
                .containsEntry("paymentUrl", "https://pay.example/alipay/order")
                .containsEntry("payType", "11");
    }

    @Test
    void doesNotSupportYishoumiWhenConfigurationIsDisabled() {
        assertThat(
                        provider(
                                        properties(false),
                                        new CapturingTransport(this::successfulResponse))
                                .supports(PaymentProviderType.YISHOUMI))
                .isFalse();
    }

    @Test
    void rejectsUpstreamBusinessError() {
        CapturingTransport transport =
                new CapturingTransport(
                        fields -> "{\"code\":500,\"msg\":\"invalid\"}");

        assertProviderFailure(transport, ErrorCode.PAYMENT_PROVIDER_UPSTREAM_FAILED);
    }

    @Test
    void rejectsInvalidResponseSignature() {
        CapturingTransport transport =
                new CapturingTransport(
                        fields ->
                                "{\"code\":0,\"msg\":\"SUCCESS!\","
                                        + "\"ordeid\":\""
                                        + fields.get("mch_orderid")
                                        + "\",\"sign\":\"invalid\","
                                        + "\"url\":\"https://pay.example/alipay/order\"}");

        assertProviderFailure(transport, ErrorCode.PAYMENT_CALLBACK_INVALID);
    }

    @Test
    void rejectsMismatchedOrderNumberAndNonHttpsPaymentUrl() {
        CapturingTransport mismatch =
                new CapturingTransport(
                        fields ->
                                signedResponse(
                                        Map.of(
                                                "code", "0",
                                                "msg", "SUCCESS!",
                                                "ordeid", "another-order",
                                                "url", "https://pay.example/order")));
        assertProviderFailure(mismatch, ErrorCode.PAYMENT_CALLBACK_INVALID);

        CapturingTransport insecureUrl =
                new CapturingTransport(
                        fields ->
                                signedResponse(
                                        Map.of(
                                                "code", "0",
                                                "msg", "SUCCESS!",
                                                "ordeid",
                                                        fields.get("mch_orderid")
                                                                .toString(),
                                                "url", "http://pay.example/order")));
        assertProviderFailure(insecureUrl, ErrorCode.PAYMENT_CALLBACK_INVALID);
    }

    private void assertProviderFailure(
            CapturingTransport transport, ErrorCode expectedCode) {
        PaymentProductEntity product = product();
        assertThatThrownBy(
                        () ->
                                provider(properties(true), transport)
                                        .createPayment(order(product), product))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expectedCode));
    }

    private String successfulResponse(Map<String, Object> request) {
        return signedResponse(
                Map.of(
                        "code", "0",
                        "msg", "SUCCESS!",
                        "ordeid", request.get("mch_orderid").toString(),
                        "url", "https://pay.example/alipay/order"));
    }

    private String requestSignatureEchoResponse(Map<String, Object> request) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", "0");
        response.put("msg", "SUCCESS!");
        response.put("ordeid", request.get("mch_orderid").toString());
        response.put("sign", request.get("sign").toString());
        response.put("url", "https://pay.example/alipay/order");
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String signedResponse(Map<String, String> responseFields) {
        Map<String, String> signed = new LinkedHashMap<>(responseFields);
        signed.put("sign", signer.sign(signed, APP_SECRET));
        try {
            return objectMapper.writeValueAsString(signed);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private YishoumiPaymentProvider provider(
            YishoumiPaymentProperties properties,
            YishoumiTransport transport) {
        return new YishoumiPaymentProvider(
                properties,
                transport,
                signer,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "fixedNonce123");
    }

    private static PaymentProductEntity product() {
        return new PaymentProductEntity(
                "SXVIP_365_DAYS", "365天会员", 26_800L, "CNY");
    }

    private static PaymentOrderEntity order(PaymentProductEntity product) {
        return new PaymentOrderEntity(
                UUID.randomUUID(),
                product,
                PaymentProviderType.YISHOUMI,
                "yishoumi-provider-test-" + UUID.randomUUID());
    }

    private static YishoumiPaymentProperties properties(boolean enabled) {
        return new YishoumiPaymentProperties(
                enabled,
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

    private static final class CapturingTransport implements YishoumiTransport {
        private final Function<Map<String, Object>, String> responder;
        private URI endpoint;
        private Map<String, Object> fields;

        private CapturingTransport(
                Function<Map<String, Object>, String> responder) {
            this.responder = responder;
        }

        @Override
        public String postJson(URI endpoint, Map<String, ?> fields) {
            this.endpoint = endpoint;
            this.fields = new LinkedHashMap<>();
            this.fields.putAll(fields);
            return responder.apply(this.fields);
        }
    }
}
