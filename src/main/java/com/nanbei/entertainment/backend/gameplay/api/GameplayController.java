package com.nanbei.entertainment.backend.gameplay.api;

import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandRequest;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandResponse;
import com.nanbei.entertainment.backend.gameplay.application.GameplayCommandService;
import com.nanbei.entertainment.backend.gameplay.application.GameplayEventService;
import com.nanbei.entertainment.backend.gameplay.application.GameplayEventView;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySessionService;
import com.nanbei.entertainment.backend.gameplay.application.GameplaySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game-sessions")
public class GameplayController {
    private final GameplaySessionService sessionService;
    private final GameplayCommandService commandService;
    private final GameplayEventService eventService;

    public GameplayController(
            GameplaySessionService sessionService,
            GameplayCommandService commandService,
            GameplayEventService eventService) {
        this.sessionService = sessionService;
        this.commandService = commandService;
        this.eventService = eventService;
    }

    @PostMapping("/{roomNumber}")
    ResponseEntity<GameplaySnapshot> open(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber) {
        GameplaySnapshot snapshot = sessionService.open(userId(jwt), roomNumber);
        return ResponseEntity.created(
                        URI.create("/api/v1/game-sessions/" + roomNumber))
                .body(snapshot);
    }

    @GetMapping("/{roomNumber}")
    GameplaySnapshot get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber) {
        return sessionService.get(userId(jwt), roomNumber);
    }

    @PostMapping("/{roomNumber}/commands")
    GameplayCommandResponse command(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GameplayCommandRequest request) {
        return commandService.submit(
                userId(jwt), roomNumber, idempotencyKey, request);
    }

    @GetMapping("/{roomNumber}/events")
    List<GameplayEventView> events(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "\\d{6}") String roomNumber,
            @RequestParam(defaultValue = "0") @PositiveOrZero long afterRevision) {
        return eventService.after(userId(jwt), roomNumber, afterRevision);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
