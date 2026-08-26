package com.nanbei.entertainment.backend.freedraw.api;

import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.RewardResponse;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.SessionResponse;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawResponses.StateResponse;
import com.nanbei.entertainment.backend.freedraw.application.FreeDrawService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/free-draw")
public class FreeDrawController {
    private final FreeDrawService service;

    public FreeDrawController(FreeDrawService service) {
        this.service = service;
    }

    @GetMapping("/state")
    StateResponse state(@AuthenticationPrincipal Jwt jwt) {
        return service.state(userId(jwt));
    }

    @PostMapping("/ad-sessions")
    SessionResponse openSession(@AuthenticationPrincipal Jwt jwt) {
        return service.openSession(userId(jwt));
    }

    @PostMapping("/ad-sessions/{sessionId}/reward")
    RewardResponse reward(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody RewardRequest request) {
        return service.claim(
                userId(jwt),
                sessionId,
                request.placementId(),
                request.adSourceId(),
                request.showId());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record RewardRequest(
            @NotBlank String placementId, String adSourceId, String showId) {}
}
