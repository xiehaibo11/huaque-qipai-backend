package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TaizhouConcurrentClaimTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void aBotHuDoesNotSkipAnotherPlayersResponseWindow() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundContext context =
                new QaRoundContext(
                        "123456",
                        "不平搓/不封顶",
                        QaTaizhouRoundEngineTest.seats(false, false, true, true),
                        QaRoundTestRigs.NOW);
        QaRoundTable table = QaRoundTable.newRound(4, 1, 1, Set.of(3, 4));
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        table.activeSeat = 1;
        table.wall.addAll(java.util.Collections.nCopies(32, 0x19));
        table.hands().get(1).add(0x25);
        table.hands().get(2).addAll(waitingFor0x25());
        table.hands().get(3).addAll(waitingFor0x25());
        table.offers()
                .put(
                        1,
                        new QaRoundTable.PendingOffer(
                                1,
                                "play-token",
                                QaPowerMask.PLAY,
                                0x25,
                                List.of(),
                                List.of(),
                                1,
                                true));

        QaRoundStep responseWindow =
                engine.apply(
                        table,
                        context,
                        1,
                        GameplayCommandType.DISCARD,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":37,\"actionToken\":\"play-token\"}"),
                        2L);

        assertThat(responseWindow.roundFinished()).isFalse();
        assertThat(responseWindow.events()).extracting(GameEvent::type)
                .containsExactly("DISCARDED", "DISCARDED", "ACTION_OFFERED");
        Map<String, Object> humanOffer = QaRoundTestRigs.lastOffer(responseWindow.events(), 2);
        assertThat((Integer) humanOffer.get("powerMask") & QaPowerMask.HU).isNotZero();
        assertThat(table.pendingClaims()).contains(new QaClaim(3, QaClaim.Kind.HU));

        QaRoundStep result =
                engine.apply(
                        table,
                        context,
                        2,
                        GameplayCommandType.PASS,
                        OBJECT_MAPPER.readTree(
                                "{\"actionToken\":\"" + humanOffer.get("actionToken") + "\"}"),
                        3L);

        assertThat(result.roundFinished()).isTrue();
        assertThat(table.outcome.winnerSeat()).isEqualTo(3);
        assertThat(table.outcome.winType()).isEqualTo("DIANPAO");
    }

    private static List<Integer> waitingFor0x25() {
        return List.of(
                0x11, 0x12, 0x13,
                0x21, 0x22, 0x23,
                0x24, 0x26,
                0x31, 0x32, 0x33,
                0x41, 0x41);
    }
}
