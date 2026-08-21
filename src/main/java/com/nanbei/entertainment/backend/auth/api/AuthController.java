package com.nanbei.entertainment.backend.auth.api;

import com.nanbei.entertainment.backend.auth.application.AuthenticationService;
import com.nanbei.entertainment.backend.auth.application.TokenPair;
import com.nanbei.entertainment.backend.auth.domain.OtpChallengeEntity;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/otp/request")
    ResponseEntity<OtpRequestedResponse> requestOtp(
            @Valid @RequestBody OtpRequest request) {
        OtpChallengeEntity challenge =
                authenticationService.requestOtp(request.phoneNumber());
        long expiresIn =
                Math.max(
                        0,
                        Duration.between(Instant.now(), challenge.getExpiresAt())
                                .toSeconds());
        return ResponseEntity.accepted()
                .body(new OtpRequestedResponse(challenge.getId(), expiresIn));
    }

    @PostMapping("/otp/verify")
    TokenPair verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return authenticationService.verifyOtp(
                request.phoneNumber(), request.code());
    }

    @PostMapping("/providers/{provider}/login")
    TokenPair providerLogin(
            @PathVariable String provider,
            @Valid @RequestBody ProviderLoginRequest request) {
        return authenticationService.loginWithProvider(
                IdentityProvider.valueOf(provider.toUpperCase(Locale.ROOT)),
                request.credential());
    }

    @PostMapping("/refresh")
    TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return authenticationService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    public record OtpRequest(@NotBlank String phoneNumber) {}

    public record OtpVerifyRequest(
            @NotBlank String phoneNumber, @NotBlank String code) {}

    public record ProviderLoginRequest(@NotBlank String credential) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record OtpRequestedResponse(UUID challengeId, long expiresIn) {}
}
