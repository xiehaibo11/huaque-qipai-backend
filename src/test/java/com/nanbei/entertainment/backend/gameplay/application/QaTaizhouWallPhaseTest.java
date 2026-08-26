package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouWallPhaseTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shengPaiStartsAtThirtyOneAndEveryFollowingDrawUpdatesIt() {
        Fixture fixture = fixture(32);

        fixture.draw(1);

        GameEvent first = onlyShengPaiEvent(fixture.events);
        assertThat(first.payload())
                .containsEntry("shengPaiCount", 31)
                .containsEntry("bFirst", true);
        assertThat(fixture.table.shengPaiCount).isEqualTo(31);

        fixture.events.clear();
        fixture.table.offers().clear();
        fixture.draw(2);

        GameEvent update = onlyShengPaiEvent(fixture.events);
        assertThat(update.payload())
                .containsEntry("shengPaiCount", 30)
                .containsEntry("bFirst", false);
        assertThat(fixture.table.shengPaiCount).isEqualTo(30);
    }

    @Test
    void yellowRoundEndsBeforeDrawingFromASeventeenTileWall() {
        Fixture fixture = fixture(17);

        fixture.draw(1);

        assertThat(fixture.table.wall).hasSize(17);
        assertThat(fixture.table.outcome).isNotNull();
        assertThat(fixture.table.outcome.winType()).isEqualTo("DRAWN");
    }

    @Test
    void drawingDownToSeventeenUpdatesShengPaiBeforeTheNextTurnYellows() {
        Fixture fixture = fixture(18);

        fixture.draw(1);

        assertThat(onlyShengPaiEvent(fixture.events).payload())
                .containsEntry("shengPaiCount", 17)
                .containsEntry("bFirst", true);
        assertThat(fixture.table.outcome).isNull();

        fixture.events.clear();
        fixture.table.offers().clear();
        fixture.draw(2);

        assertThat(fixture.table.wall).hasSize(17);
        assertThat(fixture.table.outcome.winType()).isEqualTo("DRAWN");
    }

    private static GameEvent onlyShengPaiEvent(List<GameEvent> events) {
        return events.stream()
                .filter(event -> event.type().equals("SHENG_PAI_COUNT"))
                .reduce((first, second) -> {
                    throw new AssertionError("more than one SHENG_PAI_COUNT event");
                })
                .orElseThrow(() -> new AssertionError("no SHENG_PAI_COUNT event"));
    }

    private static Fixture fixture(int wallSize) {
        QaTaizhouProjection projection = new QaTaizhouProjection(OBJECT_MAPPER);
        QaRoundEventFactory eventFactory = new QaRoundEventFactory(projection);
        QaRoundTurnDriver driver =
                new QaRoundTurnDriver(
                        eventFactory, new QaTaizhouBotPolicy(), new QaTingInfoCalculator());
        QaRoundTable table = QaRoundTable.newRound(4, 1, 1, List.of());
        for (int index = 0; index < wallSize; index++) {
            table.wall.add(0x19);
        }
        return new Fixture(table, QaRoundTestRigs.humanDealerContext(), driver, new ArrayList<>());
    }

    private record Fixture(
            QaRoundTable table,
            QaRoundContext context,
            QaRoundTurnDriver driver,
            List<GameEvent> events) {
        void draw(int seat) {
            table.activeSeat = seat;
            table.stage = QaRoundTable.Stage.AWAIT_DRAW;
            driver.beginTurn(table, context, 2L, events);
        }
    }
}
