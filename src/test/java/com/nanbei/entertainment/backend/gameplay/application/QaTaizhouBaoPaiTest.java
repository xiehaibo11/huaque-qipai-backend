package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouBaoPaiTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void preBaoIndexesOnlyContainRawDiscardsThatCanTriggerShengPaiBao() {
        Fixture fixture = fixture(4);
        fixture.recordDiscard(1, 0x11);
        fixture.table.hands().get(2).addAll(List.of(0x11, 0x12));
        fixture.table.shengPaiCount = 31;
        fixture.table.offers().put(
                2,
                new QaRoundTable.PendingOffer(
                        1, "play-token", QaPowerMask.PLAY, null, List.of(), List.of(), 0, true));

        Map<?, ?> permission = (Map<?, ?>) QaTaizhouPlayPermissions.bySeat(fixture.table).get("2");

        assertThat((List<?>) permission.get("preBaoOriginalIndexes")).isEqualTo(List.of(2));
    }

    @Test
    void rawShengPaiDiscardMakesTheDiscarderPayForTheWholeTable() {
        Fixture fixture = fixture(4);
        fixture.recordDiscard(1, 0x11);
        fixture.table.hands().get(2).addAll(List.of(0x11, 0x25));
        fixture.table.hands().get(3).addAll(
                List.of(
                        0x11, 0x11, 0x11,
                        0x12, 0x13, 0x14,
                        0x21, 0x22, 0x23,
                        0x31, 0x32, 0x33,
                        0x25));
        fixture.table.shengPaiCount = 31;

        fixture.driver.discard(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 2, 0x25);
        fixture.driver.declareWin(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 3, "DIANPAO", 2, 0x25);

        assertThat(fixture.table.outcome.endStates()).containsEntry(2, "EPS_CHENGBAO");
        assertThat(fixture.table.outcome.deltas().get(1)).isZero();
        assertThat(fixture.table.outcome.deltas().get(4)).isZero();
        assertThat(fixture.table.totalResult.seats().get(2).chengBaoNum()).isEqualTo(1);
    }

    @Test
    void twoPlayerGoldRoomNeverEnablesBaoPai() {
        Fixture fixture = fixture(2);
        fixture.recordDiscard(1, 0x11);
        fixture.table.hands().get(1).addAll(List.of(0x11, 0x25));
        fixture.table.hands().get(2).addAll(
                List.of(
                        0x11, 0x11, 0x11,
                        0x12, 0x13, 0x14,
                        0x21, 0x22, 0x23,
                        0x31, 0x32, 0x33,
                        0x25));
        fixture.table.shengPaiCount = 31;

        fixture.driver.discard(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 1, 0x25);
        fixture.driver.declareWin(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 2, "DIANPAO", 1, 0x25);

        assertThat(fixture.table.outcome.endStates()).doesNotContainValue("EPS_CHENGBAO");
    }

    @Test
    void threeMeldProviderPaysWhenThePureSuitWinnerSelfDraws() {
        Fixture fixture = fixture(4);
        fixture.table.hands().get(3).addAll(List.of(0x11, 0x12, 0x13, 0x19, 0x19));
        addThreePureMelds(fixture.table, 3, 2);

        fixture.driver.declareWin(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 3, "ZIMO", null, 0x19);

        assertThat(fixture.table.outcome.endStates()).containsEntry(2, "EPS_CHENGBAO");
    }

    @Test
    void discarderPaysWhenThreePureMeldsAreWaitingForTheSameSuit() {
        Fixture fixture = fixture(4);
        fixture.table.hands().get(3).addAll(List.of(0x11, 0x12, 0x13, 0x19));
        fixture.table.hands().get(4).addAll(List.of(0x41, 0x19));
        addThreePureMelds(fixture.table, 3, 2);

        fixture.driver.discard(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 4, 0x19);
        fixture.driver.declareWin(
                fixture.table, fixture.context, 2L, new ArrayList<>(), 3, "DIANPAO", 4, 0x19);

        assertThat(fixture.table.outcome.endStates()).containsEntry(4, "EPS_CHENGBAO");
    }

    @Test
    void preBaoWarnsBeforeDiscardingIntoAThreeMeldPureSuitWait() {
        Fixture fixture = fixture(4);
        fixture.table.hands().get(3).addAll(List.of(0x11, 0x12, 0x13, 0x19));
        fixture.table.hands().get(4).addAll(List.of(0x41, 0x19));
        addThreePureMelds(fixture.table, 3, 2);
        fixture.table.offers().put(
                4,
                new QaRoundTable.PendingOffer(
                        1, "play-token", QaPowerMask.PLAY, null, List.of(), List.of(), 0, true));

        Map<?, ?> permission = (Map<?, ?>) QaTaizhouPlayPermissions.bySeat(fixture.table).get("4");

        assertThat((List<?>) permission.get("preBaoOriginalIndexes")).isEqualTo(List.of(2));
    }

    @Test
    void threeMeldProviderIsProjectedAsAnAuthoritativeChengBaoFlag() {
        Fixture fixture = fixture(4);
        addThreePureMelds(fixture.table, 3, 2);
        QaRoundStateCodec codec =
                new QaRoundStateCodec(
                        OBJECT_MAPPER, new QaTaizhouProjection(OBJECT_MAPPER));

        tools.jackson.databind.JsonNode flags =
                codec.sessionState(fixture.table, fixture.context).path("chengBaoFlagsBySeat");

        assertThat(flags.path("1").asBoolean()).isFalse();
        assertThat(flags.path("2").asBoolean()).isTrue();
        assertThat(flags.path("3").asBoolean()).isFalse();
        assertThat(flags.path("4").asBoolean()).isFalse();
    }

    private static void addThreePureMelds(QaRoundTable table, int winner, int provider) {
        table.melds().get(winner).add(
                new QaRoundTable.Meld("CHOW", List.of(0x14, 0x15, 0x16), provider));
        table.melds().get(winner).add(
                new QaRoundTable.Meld("PONG", List.of(0x17, 0x17, 0x17), provider));
        table.melds().get(winner).add(
                new QaRoundTable.Meld(
                        "EXPOSED_KONG", List.of(0x18, 0x18, 0x18, 0x18), provider));
    }

    private static Fixture fixture(int chairCount) {
        QaTaizhouProjection projection = new QaTaizhouProjection(OBJECT_MAPPER);
        QaRoundEventFactory eventFactory = new QaRoundEventFactory(projection);
        QaRoundTurnDriver driver =
                new QaRoundTurnDriver(
                        eventFactory, new QaTaizhouBotPolicy(), new QaTingInfoCalculator());
        QaRoundTable table = QaRoundTable.newRound(chairCount, 1, 1, List.of());
        table.goldMode = true;
        table.baseScore = 1;
        for (int seat = 1; seat <= chairCount; seat++) {
            table.openingCoinsBySeat.put(seat, 100_000L);
            table.choices().put(seat, "PASS");
        }
        QaRoundContext context =
                new QaRoundContext(
                        "123456",
                        "底分 1/撩搭子包牌/不死包",
                        chairCount == 2
                                ? QaTaizhouRoundEngineTest.seats(false, false)
                                : QaTaizhouRoundEngineTest.seats(false, false, false, false),
                        QaRoundTestRigs.NOW,
                        true);
        return new Fixture(table, context, driver);
    }

    private record Fixture(
            QaRoundTable table, QaRoundContext context, QaRoundTurnDriver driver) {
        void recordDiscard(int seat, int tile) {
            table.hands().get(seat).add(tile);
            driver.discard(table, context, 1L, new ArrayList<GameEvent>(), seat, tile);
            table.offers().clear();
        }
    }
}
