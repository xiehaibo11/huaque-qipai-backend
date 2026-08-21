package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.auth.application.VerifiedMobile;
import com.nanbei.entertainment.backend.common.config.OneTapProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.one-tap",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnavailableOneTapMobileExchange
        implements OneTapMobileExchange {
    private final OneTapProperties properties;

    public UnavailableOneTapMobileExchange(
            OneTapProperties properties) {
        this.properties = properties;
    }

    @Override
    public VerifiedMobile exchange(String accessToken, String outId) {
        String message =
                properties.enabled()
                        ? "本机号码认证服务适配器尚未配置"
                        : "本机号码认证服务尚未启用";
        throw new ApiException(
                ErrorCode.AUTH_PROVIDER_UNAVAILABLE, message);
    }
}
