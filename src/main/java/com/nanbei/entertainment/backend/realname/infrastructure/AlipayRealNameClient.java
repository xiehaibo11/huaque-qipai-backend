package com.nanbei.entertainment.backend.realname.infrastructure;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayUserInfoShareRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import com.nanbei.entertainment.backend.common.config.AlipayRealNameProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.realname.application.AlipayRealName;
import com.nanbei.entertainment.backend.realname.application.AlipayRealNameExchanger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 支付宝实名信息换取适配器：先用 authCode 换 access_token，
 * 再通过 alipay.user.info.share 读取 userName 与 certNo。
 * 返回这两个字段需要支付宝开放平台的会员实名信息授权资质；
 * 资质缺失时支付宝不返回字段，按服务不可用处理。
 * 日志绝不记录姓名或证件号。
 */
@Component
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.alipay-realname",
        name = "enabled",
        havingValue = "true")
public class AlipayRealNameClient implements AlipayRealNameExchanger {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(AlipayRealNameClient.class);
    private static final String GATEWAY =
            "https://openapi.alipay.com/gateway.do";

    private final AlipayRealNameProperties properties;
    private final AlipayClient alipayClient;

    public AlipayRealNameClient(AlipayRealNameProperties properties) {
        this(properties, buildClient(properties));
    }

    AlipayRealNameClient(
            AlipayRealNameProperties properties, AlipayClient alipayClient) {
        this.properties = properties;
        this.alipayClient = alipayClient;
    }

    @Override
    public AlipayRealName exchange(String authCode) {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    ErrorCode.REALNAME_UNAVAILABLE,
                    "支付宝实名认证服务尚未启用");
        }
        String accessToken = exchangeAccessToken(authCode);
        return fetchRealName(accessToken);
    }

    private String exchangeAccessToken(String authCode) {
        AlipaySystemOauthTokenRequest request =
                new AlipaySystemOauthTokenRequest();
        request.setGrantType("authorization_code");
        request.setCode(authCode);
        try {
            AlipaySystemOauthTokenResponse response =
                    alipayClient.execute(request);
            if (response.isSuccess()
                    && response.getAccessToken() != null
                    && !response.getAccessToken().isBlank()) {
                return response.getAccessToken();
            }
            LOGGER.warn(
                    "Alipay oauth token exchange rejected, code={}",
                    response.getCode());
            throw new ApiException(
                    ErrorCode.REALNAME_INVALID_FORMAT,
                    "支付宝授权凭证无效或已过期");
        } catch (AlipayApiException exception) {
            LOGGER.warn(
                    "Alipay oauth token exchange failed, failureType={}",
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private AlipayRealName fetchRealName(String accessToken) {
        AlipayUserInfoShareRequest request =
                new AlipayUserInfoShareRequest();
        try {
            AlipayUserInfoShareResponse response =
                    alipayClient.execute(request, accessToken);
            if (!response.isSuccess()) {
                LOGGER.warn(
                        "Alipay user info share rejected, code={}",
                        response.getCode());
                throw unavailable();
            }
            String userName = response.getUserName();
            String certNo = response.getCertNo();
            if (userName == null
                    || userName.isBlank()
                    || certNo == null
                    || certNo.isBlank()) {
                LOGGER.warn(
                        "Alipay user info share returned no certified"
                                + " real-name fields");
                throw unavailable();
            }
            return new AlipayRealName(userName, certNo);
        } catch (AlipayApiException exception) {
            LOGGER.warn(
                    "Alipay user info share failed, failureType={}",
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private static AlipayClient buildClient(
            AlipayRealNameProperties properties) {
        return new DefaultAlipayClient(
                GATEWAY,
                nullToEmpty(properties.appId()),
                nullToEmpty(properties.privateKey()),
                "json",
                "UTF-8",
                nullToEmpty(properties.alipayPublicKey()),
                "RSA2");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ApiException unavailable() {
        return new ApiException(
                ErrorCode.REALNAME_UNAVAILABLE,
                "支付宝实名认证服务暂不可用，请稍后重试");
    }
}
