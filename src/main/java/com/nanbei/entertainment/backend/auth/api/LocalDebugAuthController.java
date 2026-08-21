package com.nanbei.entertainment.backend.auth.api;

import com.nanbei.entertainment.backend.auth.application.AuthenticationService;
import com.nanbei.entertainment.backend.auth.application.TokenPair;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/auth")
public class LocalDebugAuthController {
    private final AuthenticationService authenticationService;

    public LocalDebugAuthController(
            AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/debug")
    TokenPair login() {
        return authenticationService.loginLocalDeveloper();
    }
}
