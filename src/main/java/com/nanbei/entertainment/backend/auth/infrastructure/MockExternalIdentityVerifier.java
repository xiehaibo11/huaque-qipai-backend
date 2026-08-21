package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.ExternalIdentity;
import com.nanbei.entertainment.backend.auth.application.ExternalIdentityVerifier;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class MockExternalIdentityVerifier implements ExternalIdentityVerifier {
    private final CryptoService cryptoService;

    public MockExternalIdentityVerifier(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public boolean supports(IdentityProvider provider) {
        return provider == IdentityProvider.WECHAT
                || provider == IdentityProvider.ONE_TAP;
    }

    @Override
    public ExternalIdentity verify(
            IdentityProvider provider, String credential) {
        if (credential == null || credential.isBlank()) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIAL, "第三方登录凭证为空");
        }
        return new ExternalIdentity(
                provider,
                "mock-" + cryptoService.sha256(provider + ":" + credential));
    }
}
