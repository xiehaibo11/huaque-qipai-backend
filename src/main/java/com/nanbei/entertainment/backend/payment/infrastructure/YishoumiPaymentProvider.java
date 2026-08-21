package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.common.config.YishoumiPaymentProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.application.PaymentProvider;
import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class YishoumiPaymentProvider implements PaymentProvider {
    private static final String PAY_TYPE_ALIPAY_H5 = "11";

    private final YishoumiPaymentProperties properties;
    private final YishoumiTransport transport;
    private final YishoumiSigner signer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;
    private final YishoumiWebhookVerifier webhookVerifier;

    @Autowired
    public YishoumiPaymentProvider(
            YishoumiPaymentProperties properties,
            YishoumiTransport transport,
            ObjectMapper objectMapper) {
        this(
                properties,
                transport,
                new YishoumiSigner(),
                objectMapper,
                Clock.systemUTC(),
                YishoumiPaymentProvider::randomNonce);
    }

    YishoumiPaymentProvider(
            YishoumiPaymentProperties properties,
            YishoumiTransport transport,
            YishoumiSigner signer,
            ObjectMapper objectMapper,
            Clock clock,
            Supplier<String> nonceSupplier) {
        this.properties = properties;
        this.transport = transport;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.nonceSupplier = nonceSupplier;
        this.webhookVerifier =
                new YishoumiWebhookVerifier(
                        properties, signer, objectMapper);
    }

    @Override
    public boolean supports(PaymentProviderType provider) {
        return provider == PaymentProviderType.YISHOUMI
                && properties.configured();
    }

    @Override
    public PaymentCreation createPayment(
            PaymentOrderEntity order, PaymentProductEntity product) {
        if (!properties.configured()) {
            throw new ApiException(
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                    "支付渠道尚未配置");
        }
        Map<String, Object> request = createRequest(order, product);
        request.put("sign", signer.sign(request, properties.appSecret()));
        Map<String, String> response =
                responseFields(
                        transport.postJson(properties.paymentUrl(), request));
        if (!"0".equals(response.get("code"))) {
            throw upstreamFailure();
        }
        String responseSignature = response.get("sign");
        boolean requestSignatureEcho =
                signer.verify(request, properties.appSecret(), responseSignature);
        boolean independentlySignedResponse =
                signer.verify(response, properties.appSecret(), responseSignature);
        if (!requestSignatureEcho && !independentlySignedResponse) {
            throw invalidResponse("支付渠道响应签名无效");
        }
        if (!order.getMerchantOrderNo().equals(response.get("ordeid"))) {
            throw invalidResponse("支付渠道返回的商户订单号不匹配");
        }
        String paymentUrl = requireHttpsPaymentUrl(response.get("url"));
        return new PaymentCreation(
                null,
                Map.of(
                        "paymentUrl", paymentUrl,
                        "payType", PAY_TYPE_ALIPAY_H5));
    }

    @Override
    public VerifiedPaymentCallback verifyWebhook(
            String rawBody, String signature) {
        return webhookVerifier.verify(rawBody);
    }

    private Map<String, Object> createRequest(
            PaymentOrderEntity order, PaymentProductEntity product) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appid", properties.appId());
        request.put("mch_orderid", order.getMerchantOrderNo());
        request.put("description", description(product.getName()));
        request.put("total", product.getAmountMinor());
        request.put("payType", Integer.parseInt(PAY_TYPE_ALIPAY_H5));
        request.put("notify_url", properties.notifyUrl().toString());
        request.put(
                "callback_url",
                returnUrl(properties.callbackUrl(), order, "success"));
        request.put(
                "nopay_url",
                returnUrl(properties.nopayUrl(), order, "cancel"));
        request.put("time", clock.instant().getEpochSecond());
        request.put("nonce_str", nonceSupplier.get());
        request.put("attach", order.getId().toString().replace("-", ""));
        return request;
    }

    private Map<String, String> responseFields(String responseBody) {
        try {
            Map<?, ?> values = objectMapper.readValue(responseBody, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (!(entry.getKey() instanceof String key)
                        || (value != null
                                && !(value instanceof String)
                                && !(value instanceof Number)
                                && !(value instanceof Boolean))) {
                    throw invalidResponse("支付渠道响应字段无效");
                }
                result.put(key, value == null ? "" : value.toString());
            }
            return result;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidResponse("支付渠道响应格式无效");
        }
    }

    private static String returnUrl(
            URI base, PaymentOrderEntity order, String outcome) {
        String separator = base.getRawQuery() == null ? "?" : "&";
        return base
                + separator
                + "orderId="
                + order.getId()
                + "&outcome="
                + outcome;
    }

    private static String description(String productName) {
        if (productName.length() <= 127) {
            return productName;
        }
        return productName.substring(0, 127);
    }

    private static String requireHttpsPaymentUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw invalidResponse("支付渠道返回了不安全的支付地址");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw invalidResponse("支付渠道返回的支付地址无效");
        }
    }

    private static String randomNonce() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ApiException upstreamFailure() {
        return new ApiException(
                ErrorCode.PAYMENT_PROVIDER_UPSTREAM_FAILED,
                "支付渠道暂时不可用，请稍后重试");
    }

    private static ApiException invalidResponse(String message) {
        return new ApiException(ErrorCode.PAYMENT_CALLBACK_INVALID, message);
    }
}
