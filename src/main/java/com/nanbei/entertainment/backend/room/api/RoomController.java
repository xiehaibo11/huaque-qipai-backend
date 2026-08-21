package com.nanbei.entertainment.backend.room.api;

import com.nanbei.entertainment.backend.room.application.RoomCatalogService;
import com.nanbei.entertainment.backend.room.application.RoomCreateCommand;
import com.nanbei.entertainment.backend.room.application.RoomGameView;
import com.nanbei.entertainment.backend.room.application.RoomService;
import com.nanbei.entertainment.backend.room.application.RoomSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomCatalogService catalogService;
    private final RoomService roomService;

    public RoomController(RoomCatalogService catalogService, RoomService roomService) {
        this.catalogService = catalogService;
        this.roomService = roomService;
    }

    @GetMapping("/games")
    List<RoomGameView> games(@RequestParam @Positive long lobbyId) {
        return catalogService.games(lobbyId);
    }

    @GetMapping("/rule-config")
    JsonNode ruleConfig(
            @RequestParam @Positive long lobbyId,
            @RequestParam @Positive long gameId) {
        return catalogService.ruleConfig(lobbyId, gameId);
    }

    @PostMapping
    ResponseEntity<RoomSnapshot> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomSnapshot room =
                roomService.create(
                        userId(jwt),
                        new RoomCreateCommand(
                                request.lobbyId(),
                                request.gameId(),
                                request.categoryIndex(),
                                request.selectedNodeNames()),
                        idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + room.roomNumber()))
                .body(room);
    }

    @GetMapping("/{roomNumber}")
    RoomSnapshot get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 6, max = 6) String roomNumber) {
        return roomService.get(userId(jwt), roomNumber);
    }

    @PostMapping("/{roomNumber}/first-round")
    RoomSnapshot firstRound(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 6, max = 6) String roomNumber) {
        return roomService.firstRound(userId(jwt), roomNumber);
    }

    @PostMapping("/{roomNumber}/join")
    RoomSnapshot join(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 6, max = 6) String roomNumber) {
        return roomService.join(userId(jwt), roomNumber);
    }

    @PostMapping("/{roomNumber}/dissolve")
    RoomSnapshot dissolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 6, max = 6) String roomNumber) {
        return roomService.dissolve(userId(jwt), roomNumber);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateRoomRequest(
            @Positive long lobbyId,
            @Positive long gameId,
            @Positive int categoryIndex,
            @NotEmpty @Size(max = 128) List<@NotBlank @Size(max = 256) String> selectedNodeNames) {}
}
