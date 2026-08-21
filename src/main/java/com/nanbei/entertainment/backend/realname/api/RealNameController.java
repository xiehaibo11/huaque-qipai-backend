package com.nanbei.entertainment.backend.realname.api;

import com.nanbei.entertainment.backend.realname.application.RealNameService;
import com.nanbei.entertainment.backend.realname.application.RealNameStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/real-name")
public class RealNameController {
    private final RealNameService realNameService;

    public RealNameController(RealNameService realNameService) {
        this.realNameService = realNameService;
    }

    @GetMapping("/status")
    RealNameStatus status(@AuthenticationPrincipal Jwt jwt) {
        return realNameService.status(userId(jwt));
    }

    @PostMapping("/verify")
    RealNameStatus verify(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RealNameVerifyRequest request) {
        return realNameService.verifyManually(
                userId(jwt), request.realName(), request.idCardNumber());
    }

    @PostMapping("/alipay/verify")
    RealNameStatus verifyWithAlipay(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AlipayVerifyRequest request) {
        return realNameService.verifyWithAlipay(
                userId(jwt), request.authCode());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record RealNameVerifyRequest(
            @NotBlank String realName, @NotBlank String idCardNumber) {}

    public record AlipayVerifyRequest(@NotBlank String authCode) {}
}
