package com.nanbei.entertainment.backend.fortune.api;

import com.nanbei.entertainment.backend.fortune.application.FortuneCaishenResponse;
import com.nanbei.entertainment.backend.fortune.application.FortunePrayerResponse;
import com.nanbei.entertainment.backend.fortune.application.FortuneService;
import com.nanbei.entertainment.backend.fortune.application.FortuneStateResponse;
import com.nanbei.entertainment.backend.fortune.application.FortuneTreasureDrawResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@RestController
@RequestMapping("/api/v1/fortune")
public class FortuneController {
    private final FortuneService service;

    public FortuneController(FortuneService service) {
        this.service = service;
    }

    @GetMapping("/state")
    FortuneStateResponse state(@AuthenticationPrincipal Jwt jwt) {
        return service.state(userId(jwt));
    }

    @PostMapping("/prayers")
    FortunePrayerResponse pray(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PrayerRequest request) {
        return service.pray(
                userId(jwt), idempotencyKey, request.productCode(), request.quantity());
    }

    @PostMapping("/treasure-draws")
    FortuneTreasureDrawResponse draw(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TreasureDrawRequest request) {
        return service.drawTreasures(userId(jwt), idempotencyKey, request.count());
    }

    @PostMapping("/caishen-activations")
    FortuneCaishenResponse activate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CaishenRequest request) {
        return service.activateCaishen(userId(jwt), idempotencyKey, request.productCode());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record PrayerRequest(
            @NotBlank String productCode,
            @Min(1) @Max(10) int quantity) {}

    public record TreasureDrawRequest(@Min(1) @Max(5) int count) {}

    public record CaishenRequest(@NotBlank String productCode) {}
}
