package com.nanbei.entertainment.backend.scoreassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerCommandService;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerDeleteResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerDetailResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerHistoryPage;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerListResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerMonthlyStatistics;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerQueryService;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreLedgerStateResponse;
import com.nanbei.entertainment.backend.scoreassistant.application.ScoreRoundResponse;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ScoreLedgerControllerTest {
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEDGER =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PLAYER =
            UUID.fromString("40000000-0000-0000-0000-000000000004");

    @Mock ScoreLedgerCommandService commandService;
    @Mock ScoreLedgerQueryService queryService;

    ScoreLedgerController controller;
    Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new ScoreLedgerController(commandService, queryService);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(OWNER.toString())
                .build();
    }

    @Test
    void createAndRoundCommandsUseOnlyJwtOwnerAndSubmittedDeltas() {
        ScoreLedgerController.CreateLedgerRequest createRequest =
                new ScoreLedgerController.CreateLedgerRequest(List.of(
                        new ScoreLedgerController.PlayerRequest("本人", true),
                        new ScoreLedgerController.PlayerRequest("牌友", false)));
        ScoreLedgerDetailResponse created = detail();
        when(commandService.create(OWNER, createRequest.toCommand())).thenReturn(created);

        assertThat(controller.create(jwt, createRequest)).isSameAs(created);

        ScoreLedgerController.RecordRoundRequest roundRequest =
                new ScoreLedgerController.RecordRoundRequest(List.of(
                        new ScoreLedgerController.ScoreRequest(PLAYER, 18L)));
        ScoreRoundResponse round = new ScoreRoundResponse(
                UUID.randomUUID(), 1, Instant.EPOCH, List.of());
        when(commandService.recordRound(OWNER, LEDGER, roundRequest.toCommand()))
                .thenReturn(round);
        assertThat(controller.recordRound(jwt, LEDGER, roundRequest)).isSameAs(round);
    }

    @Test
    void lifecycleCommandsAreOwnedByJwtSubject() {
        ScoreLedgerStateResponse state = new ScoreLedgerStateResponse(
                LEDGER, ScoreLedgerStatus.ENDED, false, 1, Instant.EPOCH);
        when(commandService.end(OWNER, LEDGER)).thenReturn(state);
        when(commandService.setFavorite(OWNER, LEDGER, true)).thenReturn(state);
        ScoreLedgerDeleteResponse deleted =
                new ScoreLedgerDeleteResponse(LEDGER, Instant.EPOCH);
        when(commandService.delete(OWNER, LEDGER)).thenReturn(deleted);

        assertThat(controller.end(jwt, LEDGER)).isSameAs(state);
        assertThat(controller.favorite(
                        jwt,
                        LEDGER,
                        new ScoreLedgerController.FavoriteRequest(true)))
                .isSameAs(state);
        assertThat(controller.delete(jwt, LEDGER)).isSameAs(deleted);
    }

    @Test
    void allQueriesUseJwtSubjectAndExposeOneBasedPaginationAndMonth() {
        ScoreLedgerListResponse active = new ScoreLedgerListResponse(List.of());
        ScoreLedgerHistoryPage history =
                new ScoreLedgerHistoryPage(2, 10, 0, 0, List.of());
        ScoreLedgerDetailResponse detail = detail();
        ScoreLedgerMonthlyStatistics monthly = new ScoreLedgerMonthlyStatistics(
                YearMonth.of(2026, 8), 0, 0, 0, 0, 0, 0, null, null);
        when(queryService.inProgress(OWNER)).thenReturn(active);
        when(queryService.history(OWNER, 2, 10)).thenReturn(history);
        when(queryService.detail(OWNER, LEDGER)).thenReturn(detail);
        when(queryService.monthly(OWNER, YearMonth.of(2026, 8))).thenReturn(monthly);

        assertThat(controller.inProgress(jwt)).isSameAs(active);
        assertThat(controller.history(jwt, 2, 10)).isSameAs(history);
        assertThat(controller.detail(jwt, LEDGER)).isSameAs(detail);
        assertThat(controller.monthly(jwt, YearMonth.of(2026, 8))).isSameAs(monthly);
        verify(queryService).history(OWNER, 2, 10);
    }

    private static ScoreLedgerDetailResponse detail() {
        return new ScoreLedgerDetailResponse(
                LEDGER,
                ScoreLedgerStatus.IN_PROGRESS,
                false,
                0,
                Instant.EPOCH,
                null,
                List.of(),
                List.of());
    }
}
