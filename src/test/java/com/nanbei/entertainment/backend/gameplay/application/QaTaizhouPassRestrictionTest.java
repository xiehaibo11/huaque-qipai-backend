package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouPassRestrictionTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TILE = 0x25;

    @Test
    void passingHuBlocksTheSameTileUntilTheSeatMovesAndSurvivesReconnect() throws Exception {
        Fixture fixture = fixture();
        fixture.table.hands().get(3).addAll(
                List.of(
                        0x11, 0x11, 0x11,
                        0x12, 0x13, 0x14,
                        0x21, 0x22, 0x23,
                        0x31, 0x32, 0x33,
                        TILE));

        fixture.discardAndPass(1, 3, TILE, QaPowerMask.HU);
        QaRoundTable restored = fixture.roundTrip();

        fixture.discard(restored, 4, TILE);

        assertThat(restored.offers()).doesNotContainKey(3);
    }

    @Test
    void passingPungBlocksTheSameTileUntilTheSeatMoves() throws Exception {
        Fixture fixture = fixture();
        fixture.table.hands().get(3).addAll(List.of(TILE, TILE));

        fixture.discardAndPass(1, 3, TILE, QaPowerMask.PUNG);
        fixture.discard(fixture.table, 4, TILE);

        assertThat(fixture.table.offers()).doesNotContainKey(3);
    }

    @Test
    void drawingClearsThePassedPungRestriction() throws Exception {
        Fixture fixture = fixture();
        fixture.table.hands().get(3).addAll(List.of(TILE, TILE));

        fixture.discardAndPass(2, 3, TILE, QaPowerMask.PUNG);
        fixture.driver.beginTurn(fixture.table, fixture.context, 2L, new ArrayList<>());
        fixture.table.offers().clear();
        fixture.discard(fixture.table, 4, TILE);

        assertThat(fixture.table.offers()).containsKey(3);
        assertThat(fixture.table.offers().get(3).powerMask & QaPowerMask.PUNG).isNotZero();
    }

    private static Fixture fixture() {
        QaTaizhouProjection projection = new QaTaizhouProjection(OBJECT_MAPPER);
        QaRoundEventFactory eventFactory = new QaRoundEventFactory(projection);
        QaRoundTurnDriver driver =
                new QaRoundTurnDriver(
                        eventFactory, new QaTaizhouBotPolicy(), new QaTingInfoCalculator());
        QaRoundTable table = QaRoundTable.newRound(4, 1, 1, List.of());
        table.jokerRule = QaTaizhouJokerRule.unrevealed();
        for (int index = 0; index < 40; index++) {
            table.wall.add(0x19);
        }
        return new Fixture(
                table,
                QaRoundTestRigs.humanDealerContext(),
                driver,
                new QaRoundCommandApplier(),
                new QaRoundStateCodec(OBJECT_MAPPER, projection));
    }

    private record Fixture(
            QaRoundTable table,
            QaRoundContext context,
            QaRoundTurnDriver driver,
            QaRoundCommandApplier applier,
            QaRoundStateCodec codec) {
        void discardAndPass(int discarder, int claimant, int tile, int expectedPower)
                throws Exception {
            discard(table, discarder, tile);
            QaRoundTable.PendingOffer offer = table.offers().get(claimant);
            assertThat(offer).isNotNull();
            assertThat(offer.powerMask & expectedPower).isNotZero();
            applier.apply(
                    driver,
                    table,
                    context,
                    claimant,
                    GameplayCommandType.PASS,
                    OBJECT_MAPPER.readTree(
                            "{\"actionToken\":\"" + offer.actionToken + "\"}"),
                    2L,
                    new ArrayList<>());
        }

        void discard(QaRoundTable target, int seat, int tile) {
            target.hands().get(seat).add(tile);
            target.activeSeat = seat;
            target.stage = QaRoundTable.Stage.AWAIT_PLAY;
            driver.discard(target, context, 2L, new ArrayList<GameEvent>(), seat, tile);
        }

        QaRoundTable roundTrip() {
            return codec.readTable(codec.sessionState(table, context));
        }
    }
}
