package com.nanbei.entertainment.backend.legacy;

import com.nanbei.entertainment.backend.auth.application.AuthenticationService;
import com.nanbei.entertainment.backend.auth.application.TokenPair;
import org.springframework.stereotype.Component;

@Component
public class LegacyAuthenticationFacade {
    private final AuthenticationService authenticationService;

    public LegacyAuthenticationFacade(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public TokenPair authenticatePhone(String phoneNumber, String code) {
        return authenticationService.verifyOtp(phoneNumber, code);
    }
}
