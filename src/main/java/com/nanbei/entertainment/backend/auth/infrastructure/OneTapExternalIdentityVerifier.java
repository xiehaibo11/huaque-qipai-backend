package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.ExternalIdentity;
import com.nanbei.entertainment.backend.auth.application.ExternalIdentityVerifier;
import com.nanbei.entertainment.backend.auth.application.MainlandPhoneNumber;
import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.auth.application.VerifiedMobile;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class OneTapExternalIdentityVerifier
        implements ExternalIdentityVerifier {
    private static final int MAX_ACCESS_TOKEN_LENGTH = 4096;

    private final OneTapMobileExchange mobileExchange;

    public OneTapExternalIdentityVerifier(
            OneTapMobileExchange mobileExchange) {
        this.mobileExchange = mobileExchange;
    }

    @Override
    public boolean supports(IdentityProvider provider) {
        return provider == IdentityProvider.ONE_TAP;
    }

    @Override
    public ExternalIdentity verify(
            IdentityProvider provider, String credential) {
        if (provider != IdentityProvider.ONE_TAP) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "第三方登录方式无效");
        }
        if (credential == null
                || credential.isBlank()
                || credential.length() > MAX_ACCESS_TOKEN_LENGTH) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIAL,
                    "本机号码授权凭证无效或已过期");
        }

        VerifiedMobile verifiedMobile =
                mobileExchange.exchange(
                        credential, UUID.randomUUID().toString());
        String phoneNumber = verifiedPhoneNumber(verifiedMobile);
        return new ExternalIdentity(
                IdentityProvider.ONE_TAP, "phone:" + phoneNumber);
    }

    private static String verifiedPhoneNumber(
            VerifiedMobile verifiedMobile) {
        if (verifiedMobile == null) {
            throw upstreamFailure();
        }
        try {
            return MainlandPhoneNumber.parse(
                            verifiedMobile.mobile())
                    .value();
        } catch (ApiException exception) {
            throw upstreamFailure();
        }
    }

    private static ApiException upstreamFailure() {
        return new ApiException(
                ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED,
                "本机号码认证服务暂不可用，请稍后重试");
    }
}
