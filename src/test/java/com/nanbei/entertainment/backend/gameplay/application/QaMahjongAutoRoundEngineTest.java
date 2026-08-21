package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class QaMahjongAutoRoundEngineTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void producesADeterministicFullQaRoundEventChainWithDrawDiscardWinAndSettlement()
            throws Exception {
        List<QaMahjongAutoRoundEngine.SeatInput> seats =
                List.of(
                        new QaMahjongAutoRoundEngine.SeatInput(
                                1,
                                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                                "房主昵称",
                                1084375590L,
                                1000L,
                                true),
                        new QaMahjongAutoRoundEngine.SeatInput(
                                2,
                                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                                "测试假人01",
                                1084375591L,
                                1000L,
                                false),
                        new QaMahjongAutoRoundEngine.SeatInput(
                                3,
                                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                                "测试假人02",
                                1084375592L,
                                1000L,
                                false),
                        new QaMahjongAutoRoundEngine.SeatInput(
                                4,
                                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                                "测试假人03",
                                1084375593L,
                                1000L,
                                false));
        QaMahjongAutoRoundEngine engine = new QaMahjongAutoRoundEngine(OBJECT_MAPPER);

        QaMahjongAutoRoundResult result =
                engine.play(
                        new QaMahjongAutoRoundEngine.Request(
                                30109L,
                                "123456",
                                4,
                                8,
                                "不平搓/不封顶",
                                0L,
                                0,
                                seats,
                                NOW));

        assertThat(result.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        assertThat(result.roundNumber()).isEqualTo(1);
        assertThat(result.revision()).isEqualTo(1L);
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence(
                        "BOT_SEATS_FILLED",
                        "WALL_SHUFFLED",
                        "MULTIPLE_CHOICE_STARTED",
                        "DEALT",
                        "DRAWN",
                        "DISCARDED",
                        "WIN_DECLARED",
                        "SCORES_SETTLED",
                        "ROUND_RESULT_READY");
        GameEvent multipleStarted =
                result.events().stream()
                        .filter(event -> event.type().equals("MULTIPLE_CHOICE_STARTED"))
                        .findFirst()
                        .orElseThrow();
        assertThat(multipleStarted.audience()).isEqualTo(GameEvent.Audience.PUBLIC);
        assertThat(multipleStarted.payload().get("qaMode")).isEqualTo(true);
        JsonNode multipleChoice =
                OBJECT_MAPPER.valueToTree(multipleStarted.payload().get("multipleChoice"));
        assertThat(multipleChoice.path("goldMode").asBoolean()).isTrue();
        assertThat(multipleChoice.path("choiceActive").asBoolean()).isTrue();
        assertThat(multipleChoice.path("baseScore").asInt()).isEqualTo(60);
        assertThat(multipleChoice.path("currentMultiplier").asInt()).isEqualTo(1);
        assertThat(multipleChoice.path("cardUseCount").asInt()).isEqualTo(1);
        assertThat(multipleChoice.path("diamondUseCount").asInt()).isEqualTo(50);
        assertThat(multipleChoice.path("allowedChoices")).extracting(JsonNode::asText)
                .containsExactly("NONE", "ADD", "SUPER");
        assertThat(multipleChoice.path("seatChoices")).hasSize(4);
        assertThat(multipleChoice.path("seatChoices").get(0).path("choice").isNull()).isTrue();
        assertThat(multipleChoice.path("seatChoices").get(1).path("choice").asText())
                .isEqualTo("NONE");
        assertThat(multipleChoice.path("seatChoices").get(2).path("choice").asText())
                .isEqualTo("ADD");
        assertThat(multipleChoice.path("seatChoices").get(3).path("choice").asText())
                .isEqualTo("SUPER");
        JsonNode firstDealtPayload =
                OBJECT_MAPPER.valueToTree(
                        result.events().stream()
                                .filter(event -> event.audience() == GameEvent.Audience.PUBLIC)
                                .filter(event -> event.type().equals("DEALT"))
                                .findFirst()
                                .orElseThrow()
                                .payload());
        assertThat(firstDealtPayload.has("multipleChoice")).isTrue();
        assertThat(firstDealtPayload.path("multipleChoice").isNull()).isTrue();
        assertThat(result.events())
                .filteredOn(event -> event.type().equals("DRAWN"))
                .hasSizeGreaterThanOrEqualTo(4);
        assertThat(result.events())
                .filteredOn(event -> event.type().equals("DISCARDED"))
                .hasSizeGreaterThanOrEqualTo(4);

        JsonNode state = result.state();
        assertThat(state.path("qaMode").asBoolean()).isTrue();
        assertThat(state.path("qaDisclosure").asText()).contains("QA");
        assertThat(state.path("visibleRoundsBySeat").path("1").path("hands")).hasSize(4);
        assertThat(state.path("visibleRoundsBySeat").path("1").path("rivers")).hasSize(4);
        assertThat(state.path("settlement").path("result").asText()).isEqualTo("ZIMO");
        assertThat(state.path("settlement").path("seats")).hasSize(4);
        assertThat(result.scoreDeltasBySeat()).containsEntry(1, 300L).containsEntry(2, -100L);
    }

    @Test
    void keepsPrivateHandsInSeatScopedEventsInsteadOfPublicRoundEvents() throws Exception {
        QaMahjongAutoRoundEngine engine = new QaMahjongAutoRoundEngine(OBJECT_MAPPER);
        QaMahjongAutoRoundResult result =
                engine.play(
                        new QaMahjongAutoRoundEngine.Request(
                                30109L,
                                "123456",
                                4,
                                8,
                                "不平搓/不封顶",
                                0L,
                                0,
                                testSeats(),
                                NOW));

        assertThat(result.events())
                .filteredOn(event -> event.audience() == GameEvent.Audience.PUBLIC)
                .allSatisfy(
                        event ->
                                assertThat(event.payload())
                                        .doesNotContainKey("visibleRound"));
        assertThat(result.events())
                .filteredOn(
                        event ->
                                event.audience() == GameEvent.Audience.SEAT
                                        && event.type().equals("DEALT"))
                .hasSize(4)
                .allSatisfy(
                        event -> {
                            assertThat(event.targetSeat()).isBetween(1, 4);
                            assertThat(event.payload()).containsKey("visibleRound");
                            assertThat(
                                            event.payload()
                                                    .get("visibleRound")
                                                    .toString())
                                    .contains("\"mySeat\":" + event.targetSeat());
                        });
    }

    private static List<QaMahjongAutoRoundEngine.SeatInput> testSeats() {
        return List.of(
                new QaMahjongAutoRoundEngine.SeatInput(
                        1,
                        UUID.fromString("10000000-0000-0000-0000-000000000001"),
                        "房主昵称",
                        1084375590L,
                        1000L,
                        true),
                new QaMahjongAutoRoundEngine.SeatInput(
                        2,
                        UUID.fromString("20000000-0000-0000-0000-000000000002"),
                        "测试假人01",
                        1084375591L,
                        1000L,
                        false),
                new QaMahjongAutoRoundEngine.SeatInput(
                        3,
                        UUID.fromString("30000000-0000-0000-0000-000000000003"),
                        "测试假人02",
                        1084375592L,
                        1000L,
                        false),
                new QaMahjongAutoRoundEngine.SeatInput(
                        4,
                        UUID.fromString("40000000-0000-0000-0000-000000000004"),
                        "测试假人03",
                        1084375593L,
                        1000L,
                        false));
    }
}
