package com.nanbei.entertainment.backend.scoreassistant.api;

import com.nanbei.entertainment.backend.scoreassistant.application.CreateScorePlayer;
import com.nanbei.entertainment.backend.scoreassistant.application.RecordScore;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerCommandService;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerDeleteResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerDetailResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerHistoryPage;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerListResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerMonthlyStatistics;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerQueryService;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerStateResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreRoundResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/score-ledgers")
public class ScoreLedgerController {
    private final ScoreLedgerCommandService commandService;
    private final ScoreLedgerQueryService queryService;

    public ScoreLedgerController(
            ScoreLedgerCommandService commandService,
            ScoreLedgerQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    ScoreLedgerDetailResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateLedgerRequest request) {
        return commandService.create(userId(jwt), request.toCommand());
    }

    @PostMapping("/{ledgerId}/rounds")
    ScoreRoundResponse recordRound(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ledgerId,
            @Valid @RequestBody RecordRoundRequest request) {
        return commandService.recordRound(userId(jwt), ledgerId, request.toCommand());
    }

    @PostMapping("/{ledgerId}/end")
    ScoreLedgerStateResponse end(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ledgerId) {
        return commandService.end(userId(jwt), ledgerId);
    }

    @PutMapping("/{ledgerId}/favorite")
    ScoreLedgerStateResponse favorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ledgerId,
            @Valid @RequestBody FavoriteRequest request) {
        return commandService.setFavorite(userId(jwt), ledgerId, request.favorite());
    }

    @DeleteMapping("/{ledgerId}")
    ScoreLedgerDeleteResponse delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ledgerId) {
        return commandService.delete(userId(jwt), ledgerId);
    }

    @GetMapping("/in-progress")
    ScoreLedgerListResponse inProgress(@AuthenticationPrincipal Jwt jwt) {
        return queryService.inProgress(userId(jwt));
    }

    @GetMapping("/history")
    ScoreLedgerHistoryPage history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return queryService.history(userId(jwt), page, pageSize);
    }

    @GetMapping("/{ledgerId}")
    ScoreLedgerDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ledgerId) {
        return queryService.detail(userId(jwt), ledgerId);
    }

    @GetMapping("/statistics/monthly")
    ScoreLedgerMonthlyStatistics monthly(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
                    @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return queryService.monthly(userId(jwt), month);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateLedgerRequest(@NotNull List<@Valid PlayerRequest> players) {
        List<CreateScorePlayer> toCommand() {
            return players.stream()
                    .map(player -> new CreateScorePlayer(player.name(), player.ownerPlayer()))
                    .toList();
        }
    }

    public record PlayerRequest(String name, boolean ownerPlayer) {}

    public record RecordRoundRequest(@NotNull List<@Valid ScoreRequest> scores) {
        List<RecordScore> toCommand() {
            return scores.stream()
                    .map(score -> new RecordScore(score.playerId(), score.scoreDelta()))
                    .toList();
        }
    }

    public record ScoreRequest(@NotNull UUID playerId, @NotNull Long scoreDelta) {}

    public record FavoriteRequest(@NotNull Boolean favorite) {}
}
