package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 超时托管代打（自建）的到期判定、代打与倒计时投影。 */
class QaRoundTimeoutFlowTest {
    private static final Instant LATER = QaRoundTestRigs.NOW.plusSeconds(40);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QaTaizhouRoundEngine engine;

    @BeforeEach
    void setUp() {
        engine = new QaTaizhouRoundEngine(objectMapper);
    }

    private static QaRoundContext contextAt(Instant now) {
        return new QaRoundContext(
                "123456",
                "不平搓/不封顶",
                QaTaizhouRoundEngineTest.seats(false, true, true, true),
                now);
    }

    @Test
    void aLivePlayOfferIsNotExpiredInsideTheGraceWindow() throws Exception {
        QaRoundStep step = QaRoundTestRigs.humanDealerAfterMultipleChoice(engine);

        assertThat(engine.hasTimedOutOffers(step.table(), QaRoundTestRigs.NOW)).isFalse();
        assertThat(
                        engine.hasTimedOutOffers(
                                step.table(),
                                QaRoundTestRigs.NOW.plusSeconds(QaRoundClock.TURN_SECONDS)))
                .isFalse();
        assertThat(
                        engine.hasTimedOutOffers(
                                step.table(),
                                QaRoundTestRigs.NOW.plusSeconds(
                                        QaRoundClock.TURN_SECONDS
                                                + QaRoundClock.SWEEP_GRACE_SECONDS)))
                .isTrue();
    }

    @Test
    void anExpiredPlayOfferIsAutoDiscardedWithTheDrawnTile() throws Exception {
        QaRoundStep step = QaRoundTestRigs.humanDealerAfterMultipleChoice(engine);
        QaRoundTable table = step.table();
        int drawnTile = table.drawnTile;

        String oldToken = QaRoundTestRigs.lastOfferToken(step.events(), 1);
        List<GameEvent> events = new ArrayList<>();
        boolean advanced =
                engine.expireTimedOutOffers(table, contextAt(LATER), step.events().size() + 100L, events);

        assertThat(advanced).isTrue();
        assertThat(events).extracting(GameEvent::type).contains("ACTION_EXPIRED", "DISCARDED");
        assertThat(table.rivers().get(1)).contains(drawnTile);
        // 被代打过牌的那枚一次性出牌权已彻底作废。
        assertThat(
                        table.offers().values().stream()
                                .noneMatch(offer -> offer.actionToken.equals(oldToken)))
                .isTrue();
    }

    @Test
    void anExpiredClaimWindowMarksUnansweredHumansAsPassed() {
        QaRoundTable table = QaRoundTable.newRound(4, 1, 0, List.of(2, 3, 4));
        table.stage = QaRoundTable.Stage.AWAIT_CLAIMS;
        QaRoundTable.PendingOffer offer =
                new QaRoundTable.PendingOffer(
                        7,
                        "claim-token",
                        QaPowerMask.PUNG,
                        21,
                        List.of(),
                        List.of(),
                        1,
                        false);
        offer.offeredAtEpochMilli =
                QaRoundTestRigs
                        .NOW
                        .minusSeconds(QaRoundClock.CLAIM_SECONDS + QaRoundClock.SWEEP_GRACE_SECONDS)
                        .toEpochMilli();
        table.offers().put(1, offer);
        QaRoundTimeoutFlow flow =
                new QaRoundTimeoutFlow(new QaRoundEventFactory(
                        new QaTaizhouProjection(objectMapper),
                        com.nanbei.entertainment.backend.gameplay.application.TaizhouRoundMode.QA));

        assertThat(flow.hasTimedOutOffers(table, QaRoundTestRigs.NOW)).isTrue();

        List<GameEvent> events = new ArrayList<>();
        boolean expired =
                flow.expireTimedOutOffers(null, table, contextAt(QaRoundTestRigs.NOW), 9L, events);

        assertThat(expired).isTrue();
        assertThat(offer.passed).isTrue();
        assertThat(events)
                .filteredOn(event -> event.type().equals("ACTION_EXPIRED"))
                .hasSize(1)
                .first()
                .satisfies(event -> assertThat(event.targetSeat()).isEqualTo(1));
    }

    @Test
    void theSnapshotClockCountsDownFromTheOfferStamp() throws Exception {
        QaRoundStep step = QaRoundTestRigs.humanDealerAfterMultipleChoice(engine);
        JsonNode stateNow = engine.sessionState(step.table(), contextAt(QaRoundTestRigs.NOW));
        JsonNode stateLater =
                engine.sessionState(
                        step.table(),
                        contextAt(QaRoundTestRigs.NOW.plusSeconds(
                                QaRoundClock.TURN_SECONDS - 4)));

        assertThat(stateNow.path("clockRemainingSeconds").asInt())
                .isEqualTo(QaRoundClock.TURN_SECONDS);
        assertThat(stateLater.path("clockRemainingSeconds").asInt()).isEqualTo(4);
    }
}
