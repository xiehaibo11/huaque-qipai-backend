package com.nanbei.entertainment.backend.timeloginact.api;

import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginActService;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.ClaimResponse;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.StateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时登录有礼。对应原版 {@code nyx/GetLoginReward} 与 {@code nyx/ClaimLoginReward} 的能力，
 * 传输层是南北娱乐自研 REST，全部端点要求 Bearer Token。
 */
@RestController
@RequestMapping("/api/v1/time-login")
public class TimeLoginActController {
    private final TimeLoginActService service;

    public TimeLoginActController(TimeLoginActService service) {
        this.service = service;
    }

    @GetMapping("/state")
    StateResponse state(@AuthenticationPrincipal Jwt jwt) {
        return service.state(userId(jwt));
    }

    @PostMapping("/claims")
    ClaimResponse claim(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ClaimRequest request) {
        return service.claimSlot(userId(jwt), idempotencyKey, request.rewardId());
    }

    @PostMapping("/wheel-draws")
    ClaimResponse drawWheel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.drawWheel(userId(jwt), idempotencyKey);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ClaimRequest(@NotBlank String rewardId) {}
}
