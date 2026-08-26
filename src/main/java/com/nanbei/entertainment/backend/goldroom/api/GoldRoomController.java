package com.nanbei.entertainment.backend.goldroom.api;

import com.nanbei.entertainment.backend.goldroom.application.GoldGameView;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomCatalogService;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomConfView;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomJoinRequest;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomJoinResponse;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomJoinService;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomLeaveRequest;
import com.nanbei.entertainment.backend.goldroom.application.GoldRoomLeaveResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

/** Gold-room (原版休闲场/大众玩法) catalog endpoints. Requires a Bearer access token. */
@RestController
@RequestMapping("/api/v1/gold-rooms")
public class GoldRoomController {
    private final GoldRoomCatalogService catalogService;
    private final GoldRoomJoinService joinService;

    public GoldRoomController(
            GoldRoomCatalogService catalogService, GoldRoomJoinService joinService) {
        this.catalogService = catalogService;
        this.joinService = joinService;
    }

    @GetMapping("/games")
    List<GoldGameView> games(@RequestParam @Positive long lobbyId) {
        return catalogService.games(lobbyId);
    }

    @GetMapping("/games/{gameId}")
    GoldRoomConfView conf(
            @PathVariable @Positive long gameId, @RequestParam @Positive long lobbyId) {
        return catalogService.conf(lobbyId, gameId);
    }

    @PostMapping("/games/{gameId}/join")
    ResponseEntity<GoldRoomJoinResponse> join(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long gameId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GoldRoomJoinRequest request) {
        GoldRoomJoinResponse response =
                joinService.join(
                        UUID.fromString(jwt.getSubject()), gameId, request, idempotencyKey);
        return ResponseEntity.status(response.replay() ? 200 : 202).body(response);
    }

    /** 原版 PlayerLeaveRequest：取消匹配时满理解锁占位，重复 leave 幂等成功。 */
    @PostMapping("/games/{gameId}/leave")
    GoldRoomLeaveResponse leave(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long gameId,
            @Valid @RequestBody GoldRoomLeaveRequest request) {
        return joinService.leave(UUID.fromString(jwt.getSubject()), gameId, request);
    }
}
