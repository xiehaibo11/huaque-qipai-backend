package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouWallOpeningTest {
    @Test
    void capturedGoldRoundsResolveTheSecondSeatAndPhysicalWallCursors() {
        // 抓包座位是 0-based；本后端座位契约是 1-based，索引仍保持抓包原值。
        assertOpening(2, List.of(4, 3), List.of(6, 1), 4, 109, 107, 108, 54);
        assertOpening(4, List.of(4, 4), List.of(3, 2), 3, 77, 75, 76, 22);
        assertOpening(3, List.of(2, 5), List.of(1, 5), 1, 9, 7, 8, 90);
        assertOpening(2, List.of(5, 6), List.of(1, 5), 4, 103, 101, 102, 48);
    }

    @Test
    void openingUsesTheCalculatedPhysicalTileAndFrontCursorForTheInitialDeal() {
        List<Integer> physicalWall = new ArrayList<>(QaTaizhouTiles.buildWall(4188L));
        QaRoundTable table = QaRoundTable.newRound(4, 2, 1, List.of());
        table.wall.addAll(physicalWall);
        QaTaizhouWallOpening opening =
                QaTaizhouWallOpening.fromDice(2, List.of(4, 3), List.of(6, 1));

        table.openWall(opening);

        assertThat(table.openTiles()).containsExactly(physicalWall.get(109));
        assertThat(table.wall).hasSize(135);
        assertThat(table.wall.get(0)).isEqualTo(physicalWall.get(107));
        assertThat(table.wallAsc).isEqualTo(107);
        assertThat(table.wallDesc).isEqualTo(108);

        for (int draw = 0; draw < 53; draw++) {
            table.drawFromWall();
        }

        assertThat(table.wall).hasSize(82);
        assertThat(table.wallAsc).isEqualTo(54);
        assertThat(table.wallDesc).isEqualTo(108);
    }

    @Test
    void roundStartEmitsBothDiceThrowsThenTheOriginalWallAndOpenWallShapes()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(objectMapper);
        QaTaizhouRoundResult started =
                engine.start(
                        QaTaizhouRoundEngineTest.request(
                                QaTaizhouRoundEngineTest.seats(false, true, true, true)));

        QaRoundStep result =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.MULTIPLE_CHOICE,
                        objectMapper.readTree("{\"choice\":\"PASS\"}"),
                        2L);

        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence(
                        "MULTIPLE_CHOICE_CHANGED",
                        "DICE_ROLLED",
                        "DICE_ROLLED",
                        "WALL_OPENED",
                        "LEFT_BANKER",
                        "DEALT");
        List<GameEvent> dice =
                result.events().stream().filter(event -> event.type().equals("DICE_ROLLED")).toList();
        assertThat(dice).hasSize(2);
        Map<String, Object> first = diceRoll(dice.get(0));
        Map<String, Object> second = diceRoll(dice.get(1));
        assertThat(first).containsEntry("nSeat", 1).containsEntry("gameStep", 4);
        assertThat(second).containsEntry("gameStep", 5);

        QaTaizhouWallOpening opening =
                QaTaizhouWallOpening.fromDice(
                        1, intValues(first.get("nChips")), intValues(second.get("nChips")));
        assertThat(second).containsEntry("nSeat", opening.secondSeat());
        GameEvent opened =
                result.events().stream()
                        .filter(event -> event.type().equals("WALL_OPENED"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> wallState = (Map<String, Object>) opened.payload().get("wallState");
        @SuppressWarnings("unchecked")
        Map<String, Object> openWall = (Map<String, Object>) opened.payload().get("openWall");
        assertThat(wallState)
                .containsEntry("nWallCnt", 135)
                .containsEntry("nAsc", opening.firstAsc())
                .containsEntry("nDesc", opening.firstDesc())
                .containsEntry("nFirstAsc", opening.firstAsc())
                .containsEntry("nFirstDesc", opening.firstDesc());
        assertThat(openWall)
                .containsEntry("nIndex", opening.openIndex())
                .containsEntry("nMah", result.table().openTiles().get(0));
        GameEvent dealt =
                result.events().stream()
                        .filter(event -> event.type().equals("DEALT"))
                        .filter(event -> event.audience() == GameEvent.Audience.PUBLIC)
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> dealtWall = (Map<String, Object>) dealt.payload().get("wallState");
        assertThat(dealtWall)
                .containsEntry("nWallCnt", 82)
                .containsEntry("nAsc", opening.ascAfterFrontDraws(53))
                .containsEntry("nDesc", opening.firstDesc());
        assertThat(engine.sessionState(result.table(), QaRoundTestRigs.humanDealerContext())
                        .path("wallState")
                        .path("nWallCnt")
                        .asInt())
                .isEqualTo(82);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> diceRoll(GameEvent event) {
        return (Map<String, Object>) event.payload().get("diceRoll");
    }

    private static List<Integer> intValues(Object value) {
        return ((List<?>) value).stream().map(item -> ((Number) item).intValue()).toList();
    }

    private static void assertOpening(
            int dealerSeat,
            List<Integer> firstDice,
            List<Integer> secondDice,
            int secondSeat,
            int openIndex,
            int firstAsc,
            int firstDesc,
            int ascAfterDeal) {
        QaTaizhouWallOpening opening =
                QaTaizhouWallOpening.fromDice(dealerSeat, firstDice, secondDice);

        assertThat(opening.secondSeat()).isEqualTo(secondSeat);
        assertThat(opening.openIndex()).isEqualTo(openIndex);
        assertThat(opening.firstAsc()).isEqualTo(firstAsc);
        assertThat(opening.firstDesc()).isEqualTo(firstDesc);
        assertThat(opening.ascAfterFrontDraws(53)).isEqualTo(ascAfterDeal);
        assertThat(opening.remainingAfterFrontDraws(53)).isEqualTo(82);
    }
}
