package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouRoundEngineTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Test
    void anAllBotTableRunsFromDealToARoundResultInsideOneRevision() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result = engine.start(request(seats(true, true, true, true)));

        assertThat(result.revision()).isEqualTo(1L);
        assertThat(result.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        assertThat(result.roundNumber()).isEqualTo(1);
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence(
                        "BOT_SEATS_FILLED",
                        "WALL_SHUFFLED",
                        "MULTIPLE_CHOICE_STARTED",
                        "DICE_ROLLED",
                        "DEALT",
                        "SCORES_SETTLED",
                        "ROUND_RESULT_READY");
        assertThat(result.events()).allSatisfy(event -> assertThat(event.revision()).isEqualTo(1L));
        JsonNode state = result.state();
        assertThat(state.path("qaMode").asBoolean()).isTrue();
        assertThat(state.path("qaDisclosure").asText()).contains("南北自建");
        assertThat(state.path("qaDisclosure").asText()).contains("非原版");
        assertThat(state.path("settlement").path("seats")).hasSize(4);
        boolean declaredWin =
                result.events().stream().anyMatch(event -> event.type().equals("WIN_DECLARED"));
        if (!declaredWin) {
            assertThat(state.path("settlement").path("result").asText()).isEqualTo("DRAWN");
        }
    }

    @Test
    void theSameSeedProducesTheSameEventChain() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult first = engine.start(request(seats(true, true, true, true)));
        QaTaizhouRoundResult second = engine.start(request(seats(true, true, true, true)));

        assertThat(first.events().stream().map(GameEvent::type).toList())
                .isEqualTo(second.events().stream().map(GameEvent::type).toList());
        assertThat(first.state().path("visibleRoundsBySeat").toString())
                .isEqualTo(second.state().path("visibleRoundsBySeat").toString());
    }

    @Test
    void aHumanDealerStopsAtAddMultipleChoiceBeforeDealing() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result = engine.start(request(seats(false, true, true, true)));

        assertThat(result.phase()).isEqualTo(GamePhase.DEALING);
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence("BOT_SEATS_FILLED", "WALL_SHUFFLED", "MULTIPLE_CHOICE_STARTED")
                .doesNotContain("DEALT", "DRAWN", "ACTION_OFFERED", "TING_INFO");
        JsonNode state = result.state();
        assertThat(state.path("multipleChoice").path("choiceActive").asBoolean()).isTrue();
        assertThat(state.path("multipleChoice").path("seatChoices")).hasSize(4);
        assertThat(state.path("multipleChoice").path("seatChoices").get(0).path("choice").isNull())
                .isTrue();
        assertThat(state.path("multipleChoice").path("seatChoices").get(1).path("choice").asText())
                .isEqualTo("DEFAULT");
        assertThat(state.path("multipleChoice").path("seatChoices").get(2).path("choice").asText())
                .isEqualTo("SUPER");
        assertThat(state.path("multipleChoice").path("seatChoices").get(3).path("choice").asText())
                .isEqualTo("PASS");
        assertThat(state.path("visibleRoundsBySeat").path("1").path("hands").get(0)
                        .path("concealedTiles"))
                .isEmpty();
        assertThat(state.path("playPermissionsBySeat").path("1").isMissingNode()).isTrue();
        assertThat(state.path("clockRemainingSeconds").asInt()).isEqualTo(5);
        assertThat(state.path("remainingWallCount").asInt()).isEqualTo(136);
    }

    @Test
    void aHumanMultipleChoiceFinishesDealingThenStopsAtTheFirstPlayOffer()
            throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaTaizhouRoundResult started =
                engine.start(request(seats(false, true, true, true)), baseDealPrefix());

        QaRoundStep result =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.MULTIPLE_CHOICE,
                        OBJECT_MAPPER.readTree("{\"choice\":\"PASS\"}"),
                        2L);

        assertThat(QaTaizhouRoundEngine.phaseOf(result.table())).isEqualTo(GamePhase.PLAYING);
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence(
                        "MULTIPLE_CHOICE_CHANGED",
                        "DICE_ROLLED",
                        "LEFT_BANKER",
                        "DEALT",
                        "ACTION_OFFERED",
                        "TING_INFO")
                .doesNotContain("DRAWN", "WIN_DECLARED", "SCORES_SETTLED", "ROUND_RESULT_READY");
        GameEvent tingInfo = result.events().get(result.events().size() - 1);
        assertThat(tingInfo.type()).isEqualTo("TING_INFO");
        assertThat(tingInfo.audience()).isEqualTo(GameEvent.Audience.SEAT);
        assertThat(tingInfo.targetSeat()).isEqualTo(1);
        GameEvent offer = result.events().get(result.events().size() - 2);
        assertThat(offer.type()).isEqualTo("ACTION_OFFERED");
        assertThat(offer.audience()).isEqualTo(GameEvent.Audience.SEAT);
        assertThat(offer.targetSeat()).isEqualTo(1);
        assertThat(offer.payload().get("seat")).isEqualTo(1);
        assertThat(offer.payload())
                .containsEntry("activeSeat", 1)
                .containsEntry("clockRemainingSeconds", 10);
        int powerMask = (Integer) offer.payload().get("powerMask");
        assertThat(powerMask & QaPowerMask.PLAY).isNotZero();
        assertThat((String) offer.payload().get("actionToken")).isNotBlank();
        assertThat(offer.payload().get("offerId")).isEqualTo(1);
        assertThat(offer.payload().get("contextTile")).isInstanceOf(Integer.class);
        assertThat((List<?>) offer.payload().get("chowCandidates")).isEmpty();
        assertThat(offer.payload().get("kongOptions")).isInstanceOf(List.class);

        JsonNode state = engine.sessionState(result.table(), QaRoundTestRigs.humanDealerContext());
        assertThat(state.path("multipleChoice").isNull()).isTrue();
        assertThat(state.path("activeSeat").asInt()).isEqualTo(1);
        assertThat(state.path("clockRemainingSeconds").asInt()).isEqualTo(10);
        JsonNode permission = state.path("playPermissionsBySeat").path("1");
        assertThat(permission.path("actionToken").asText())
                .isEqualTo((String) offer.payload().get("actionToken"));
        assertThat(permission.path("playableOriginalIndexes")).isNotEmpty();

        // D1 回归：听牌角标索引必须真的下发（此前恒为空数组，drawTingIcon 永远拿不到数据），
        // 且必须是可打出索引的子集、牌值命中同一回合 TING_INFO 的非空 huTargets 项。
        JsonNode tingIndexes = permission.path("tingOriginalIndexes");
        assertThat(tingIndexes.isArray()).isTrue();
        Set<Integer> playable = new LinkedHashSet<>();
        permission.path("playableOriginalIndexes").forEach(n -> playable.add(n.asInt()));
        Set<Integer> tingDiscards = QaRoundTestRigs.tingDiscardsFor(result.events(), 1);
        JsonNode tingHand = state.path("visibleRoundsBySeat").path("1").path("hands").get(0);
        tingIndexes.forEach(
                n -> {
                    assertThat(playable).contains(n.asInt());
                    assertThat(tingDiscards)
                            .contains(QaRoundTestRigs.tileValueAt(tingHand, n.asInt()));
                });
        JsonNode seatOneHand =
                state.path("visibleRoundsBySeat").path("1").path("hands").get(0);
        int concealedCount = seatOneHand.path("concealedTiles").size();
        assertThat(concealedCount).isEqualTo(13);
        assertThat(state.path("visibleRoundsBySeat").path("1").path("hands").get(1)
                        .path("concealedTiles"))
                .hasSize(13);
        assertThat(state.path("visibleRoundsBySeat").path("1").path("hands").get(2)
                        .path("concealedTiles"))
                .hasSize(13);
        assertThat(state.path("visibleRoundsBySeat").path("1").path("hands").get(3)
                        .path("concealedTiles"))
                .hasSize(13);
        assertThat(seatOneHand.path("drawnTile").asInt()).isEqualTo(result.table().drawnTile);
        assertThat(jsonIntList(permission.path("playableOriginalIndexes")))
                .as("Android uses original index 0 only for drawnTile; concealed tiles are 1-based")
                .containsExactlyElementsOf(withDrawnTileIndex(concealedCount));
        assertThat(result.events()).extracting(GameEvent::type).doesNotContain("FLOWER_REPLACED");
        assertThat(state.path("remainingWallCount").asInt()).isEqualTo(136 - 1 - 53);
        assertThat(state.path("qaRound").path("openTiles").get(0).asInt()).isEqualTo(0x31);
        assertThat(state.path("visibleRoundsBySeat").path("1").path("jokerTiles").get(0).asInt())
                .isEqualTo(0x31);
        assertThat(state.path("visibleRoundsBySeat").path("1").path("insteadTiles").get(0).asInt())
                .isEqualTo(0x53);
    }

    @Test
    void actionOffersAreOnlyVisibleToTheirTargetSeatAndPublicEventsHideHands() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result = engine.start(request(seats(false, true, true, true)));

        assertThat(result.events())
                .filteredOn(event -> event.type().equals("ACTION_OFFERED"))
                .allSatisfy(
                        event -> {
                            assertThat(event.audience()).isEqualTo(GameEvent.Audience.SEAT);
                            assertThat(event.payload().get("seat")).isEqualTo(event.targetSeat());
                        });
        assertThat(result.events())
                .filteredOn(event -> event.audience() == GameEvent.Audience.PUBLIC)
                .allSatisfy(
                        event -> assertThat(event.payload()).doesNotContainKey("visibleRound"));
    }

    @Test
    void dazhongWallUses136PlayableTilesWithoutFlowers() {
        List<Integer> wall = QaTaizhouTiles.buildWall(12345L);

        assertThat(wall).hasSize(136);
        assertThat(wall).allSatisfy(tile -> assertThat(QaTaizhouTiles.isPlayable(tile)).isTrue());
        assertThat(wall).noneMatch(QaTaizhouTiles::isWallFlower);
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int tile : wall) {
            counts.merge(tile, 1, Integer::sum);
        }
        assertThat(counts).hasSize(34);
        assertThat(counts.values()).containsOnly(4);
    }

    @Test
    void dazhongRoundDoesNotEmitFlowerReplacementEvents() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result =
                engine.start(request(seats(true, true, true, true)), baseDealPrefix());

        assertThat(result.events()).extracting(GameEvent::type).doesNotContain("FLOWER_REPLACED");
        result.state().path("visibleRoundsBySeat").path("1").path("hands")
                .forEach(seat -> assertThat(seat.path("flowers")).isEmpty());
    }

    @Test
    void openingWhiteDoesNotCreateAnInsteadTile() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        List<Integer> wall = new ArrayList<>(java.util.Collections.nCopies(136, 0x53));

        QaTaizhouRoundResult started = engine.start(request(seats(false, true, true, true)), wall);
        QaRoundStep result =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.MULTIPLE_CHOICE,
                        OBJECT_MAPPER.readTree("{\"choice\":\"PASS\"}"),
                        2L);

        assertThat(result.table().jokerRule.jokerTiles()).isEqualTo(List.of(0x53));
        assertThat(result.table().jokerRule.insteadTiles()).isEmpty();
        assertThat(result.table().wall).hasSize(136 - 1 - 53);
    }

    @Test
    void botTurnAdvancedDoesNotExposeClientPlaybackDelay() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result =
                engine.start(request(seats(true, true, true, true)), baseDealPrefix());

        List<GameEvent> turns =
                result.events().stream()
                        .filter(event -> event.type().equals("TURN_ADVANCED"))
                        .toList();
        assertThat(turns).isNotEmpty();
        assertThat(turns)
                .allSatisfy(
                        event -> {
                            assertThat(event.payload()).doesNotContainKey("playbackDelayMillis");
                            assertThat(event.payload())
                                    .containsEntry("clockRemainingSeconds", QaRoundClock.TURN_SECONDS);
                        });
    }

    @Test
    void botDealerOpeningDiscardFollowsTurnAdvancedWithoutPlaybackBoundary() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result =
                engine.start(request(seats(true, true, true, true)), baseDealPrefix());

        List<GameEvent> events = result.events();
        int dealtIndex = firstIndexOf(events, "DEALT");
        int turnIndex = firstIndexOf(events, "TURN_ADVANCED");
        int discardIndex = firstIndexOf(events, "DISCARDED");
        assertThat(discardIndex).isPositive();
        assertThat(turnIndex).isGreaterThan(dealtIndex).isLessThan(discardIndex);

        GameEvent turn = events.get(turnIndex);
        assertThat(turn.audience()).isEqualTo(GameEvent.Audience.PUBLIC);
        assertThat(turn.payload()).containsEntry("activeSeat", 1);
        assertThat(turn.payload()).doesNotContainKey("playbackDelayMillis");
        assertThat(turn.payload()).containsEntry("clockRemainingSeconds", QaRoundClock.TURN_SECONDS);
    }

    @Test
    void anExhaustedWallEndsTheRoundAsADrawWithoutAWinner() {
        List<Integer> wall = new ArrayList<>(baseDealPrefix().subList(0, 54));
        // 1 张翻得 + 13 巡发牌 + 庄家起手第 14 张；庄家打出后下家摸牌时墙尽。
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result = engine.start(request(seats(true, true, true, true)), wall);

        assertThat(result.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        assertThat(result.events())
                .extracting(GameEvent::type)
                .doesNotContain("WIN_DECLARED")
                .containsSubsequence("SCORES_SETTLED", "ROUND_RESULT_READY");
        JsonNode settlement = result.state().path("settlement");
        assertThat(settlement.path("result").asText()).isEqualTo("DRAWN");
        assertThat(settlement.path("seats"))
                .allSatisfy(seat -> assertThat(seat.path("endPlayerState").asText())
                        .isEqualTo("EPS_DRAWN"));
        assertThat(result.scoreDeltasBySeat().values()).containsOnly(0L);
    }

    @Test
    void theConfiguredFinalRoundEmitsAndPersistsTheTotalResult() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaTaizhouRoundEngine.Request request =
                new QaTaizhouRoundEngine.Request(
                        30109L,
                        "123456",
                        4,
                        1,
                        "不平搓/不封顶",
                        0L,
                        0,
                        seats(true, true, true, true),
                        NOW);

        QaTaizhouRoundResult result = engine.start(request, baseDealPrefix());

        assertThat(result.events()).extracting(GameEvent::type)
                .endsWith("ROUND_RESULT_READY", "TOTAL_RESULT_READY");
        assertThat(result.state().path("totalResult").path("playCount").asInt()).isEqualTo(1);
        assertThat(result.state().path("totalResult").path("originalMsgTotalResult")
                        .path("XY_ID").asInt())
                .isEqualTo(1038);
        assertThatThrownBy(
                        () ->
                                engine.apply(
                                        result.table(),
                                        QaRoundTestRigs.allBotContext(),
                                        1,
                                        GameplayCommandType.NEXT_ROUND,
                                        null,
                                        2L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    static QaTaizhouRoundEngine.Request request(List<QaMahjongAutoRoundEngine.SeatInput> seats) {
        return new QaTaizhouRoundEngine.Request(
                30109L, "123456", seats.size(), 8, "不平搓/不封顶", 0L, 0, seats, NOW);
    }

    static List<QaMahjongAutoRoundEngine.SeatInput> seats(boolean... bots) {
        List<QaMahjongAutoRoundEngine.SeatInput> seats = new ArrayList<>();
        for (int index = 0; index < bots.length; index++) {
            seats.add(
                    new QaMahjongAutoRoundEngine.SeatInput(
                            index + 1,
                            UUID.fromString(
                                    String.format(
                                            "10000000-0000-0000-0000-%012d", index + 1)),
                            bots[index] ? "A-伟娜" + (index + 1) : "房主昵称",
                            1084375590L + index,
                            1000L,
                            bots[index]));
        }
        return seats;
    }

    private static List<Integer> oneBasedIndexes(int count) {
        List<Integer> result = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            result.add(index);
        }
        return result;
    }

    private static List<Integer> withDrawnTileIndex(int concealedCount) {
        List<Integer> result = new ArrayList<>(concealedCount + 1);
        result.add(0);
        result.addAll(oneBasedIndexes(concealedCount));
        return result;
    }

    private static int firstIndexOf(List<GameEvent> events, String type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).type().equals(type)) {
                return index;
            }
        }
        return -1;
    }

    private static List<Integer> jsonIntList(JsonNode node) {
        List<Integer> result = new ArrayList<>();
        for (JsonNode value : node) {
            result.add(value.asInt());
        }
        return result;
    }

    /**
     * Builds a deterministic 136-card wall whose first 52 cards deal an
     * unremarkable 13-card hand to every seat; the next tile becomes the
     * dealer's opening 14th tile.
     */
    static List<Integer> baseDealPrefix() {
        List<Integer> deal = new ArrayList<>(QaTaizhouTiles.WALL_SIZE);
        int[] fillers = {
            0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x41, 0x42, 0x43, 0x44
        };
        for (int round = 0; round < 13; round++) {
            for (int seat = 0; seat < 4; seat++) {
                deal.add(fillers[(round + seat) % fillers.length]);
            }
        }
        List<Integer> suffix = new ArrayList<>();
        for (int suit : List.of(0x10, 0x20, 0x30)) {
            for (int rank = 1; rank <= 9; rank++) {
                suffix.add(suit + rank);
            }
        }
        for (int rank = 1; rank <= 4; rank++) {
            suffix.add(0x40 + rank);
        }
        for (int rank = 1; rank <= 3; rank++) {
            suffix.add(0x50 + rank);
        }
        // 摸牌段只放基础牌（不含花牌），避免未预期的补花干扰用例。
        while (deal.size() + suffix.size() < QaTaizhouTiles.WALL_SIZE) {
            suffix.addAll(new ArrayList<>(suffix));
        }
        deal.addAll(suffix.subList(0, QaTaizhouTiles.WALL_SIZE - deal.size()));
        return deal;
    }
}
