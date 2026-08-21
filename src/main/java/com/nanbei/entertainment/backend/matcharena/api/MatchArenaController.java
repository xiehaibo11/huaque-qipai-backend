package com.nanbei.entertainment.backend.matcharena.api;

import com.nanbei.entertainment.backend.matcharena.application.MatchArenaCreateCommand;
import com.nanbei.entertainment.backend.matcharena.application.MatchArenaListResponse;
import com.nanbei.entertainment.backend.matcharena.application.MatchArenaResponse;
import com.nanbei.entertainment.backend.matcharena.application.MatchArenaService;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaCostType;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaLevel;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/match-arenas")
public class MatchArenaController {
    private final MatchArenaService service;

    public MatchArenaController(MatchArenaService service) {
        this.service = service;
    }

    @GetMapping
    MatchArenaListResponse list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @PostMapping
    ResponseEntity<MatchArenaResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRequest request) {
        MatchArenaResponse response =
                service.create(userId(jwt), request.toCommand(), idempotencyKey);
        if (response.duplicate()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateRequest(
            @Positive long lobbyId,
            String remark,
            @NotNull MatchArenaLevel level,
            @NotNull MatchArenaMode mode,
            @NotNull MatchArenaCostType costType,
            @Min(0) long initialRoomCards,
            @Positive long dailyRoomCardLimit,
            boolean visibleToStrangers,
            boolean autoTransferEnabled,
            @Positive long autoTransferThreshold,
            @Min(0) long autoTransferAmount,
            @Positive Long lowCardReminderThreshold) {
        MatchArenaCreateCommand toCommand() {
            return new MatchArenaCreateCommand(
                    lobbyId,
                    remark,
                    level,
                    mode,
                    costType,
                    initialRoomCards,
                    dailyRoomCardLimit,
                    visibleToStrangers,
                    autoTransferEnabled,
                    autoTransferThreshold,
                    autoTransferAmount,
                    lowCardReminderThreshold);
        }
    }
}
