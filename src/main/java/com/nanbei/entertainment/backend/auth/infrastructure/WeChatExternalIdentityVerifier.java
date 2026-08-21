package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.ExternalIdentity;
import com.nanbei.entertainment.backend.auth.application.ExternalIdentityVerifier;
import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class WeChatExternalIdentityVerifier
        implements ExternalIdentityVerifier {
    private final WeChatProperties properties;
    private final WeChatCodeExchange codeExchange;

    public WeChatExternalIdentityVerifier(
            WeChatProperties properties, WeChatCodeExchange codeExchange) {
        this.properties = properties;
        this.codeExchange = codeExchange;
    }

    @Override
    public boolean supports(IdentityProvider provider) {
        return provider == IdentityProvider.WECHAT;
    }

    @Override
    public ExternalIdentity verify(
            IdentityProvider provider, String credential) {
        if (provider != IdentityProvider.WECHAT) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "第三方登录方式无效");
        }
        if (!properties.isConfigured()) {
            throw new ApiException(
                    ErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                    "微信登录服务尚未启用");
        }
        if (credential == null || credential.isBlank()) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIAL,
                    "微信授权凭证无效或已过期");
        }

        WeChatTokenResponse response = codeExchange.exchange(credential);
        String unionId = trimmed(response.unionid());
        if (!unionId.isEmpty()) {
            return new ExternalIdentity(
                    IdentityProvider.WECHAT, "unionid:" + unionId);
        }
        String openId = trimmed(response.openid());
        if (openId.isEmpty()) {
            throw new ApiException(
                    ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED,
                    "微信登录服务暂不可用，请稍后重试");
        }
        return new ExternalIdentity(
                IdentityProvider.WECHAT,
                "appid:"
                        + properties.appId().trim()
                        + ":openid:"
                        + openId);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
