package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TING_INFO、SHENG_PAI_COUNT、LEFT_BANKER 的引擎级事件与快照契约
 * （全部为南北自建 QA 规则，非原版服务端算法；语义分别对齐
 * msgTingMahInfo 562 / msgShengPaiCnt 1049 / msgLeftBanker 1050）。
 */
class QaRoundWave3EventTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void aHumanPlayOfferIsFollowedByASeatScopedTingInfoEventAndSnapshotEntry()
            throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaRoundStep result =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaTaizhouRoundEngineTest.baseDealPrefix());

        GameEvent offer = result.events().get(result.events().size() - 2);
        assertThat(offer.type()).isEqualTo("ACTION_OFFERED");
        GameEvent tingInfo = result.events().get(result.events().size() - 1);
        assertThat(tingInfo.type()).isEqualTo("TING_INFO");
        assertThat(tingInfo.audience()).isEqualTo(GameEvent.Audience.SEAT);
        assertThat(tingInfo.targetSeat()).isEqualTo(1);
        assertThat(tingInfo.payload().get("seat")).isEqualTo(1);
        assertThat(tingInfo.payload().get("tingMahs")).isInstanceOf(List.class);
        List<?> tingMahs = (List<?>) tingInfo.payload().get("tingMahs");
        tingMahs.forEach(
                entry -> {
                    Map<?, ?> ting = (Map<?, ?>) entry;
                    assertThat(ting.get("discard")).isInstanceOf(Integer.class);
                    assertThat((List<?>) ting.get("huTargets")).isNotEmpty();
                });

        JsonNode state = engine.sessionState(result.table(), QaRoundTestRigs.humanDealerContext());
        JsonNode snapshotTing = state.path("tingInfosBySeat").path("1");
        assertThat(snapshotTing.path("seat").asInt()).isEqualTo(1);
        assertThat(snapshotTing.path("tingMahs").toString())
                .isEqualTo(OBJECT_MAPPER.valueToTree(tingMahs).toString());
    }

    @Test
    void discardingClearsTheTingInfoAndTheNextOfferRecomputesItFromTheLiveHand() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep result =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaTaizhouRoundEngineTest.baseDealPrefix());
        String token = QaRoundTestRigs.lastOfferToken(result.events(), 1);
        int tile = result.table().hands().get(1).get(0);

        QaRoundStep step =
                engine.apply(
                        result.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.DISCARD,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":" + tile + ",\"actionToken\":\"" + token + "\"}"),
                        3L);

        assertThat(step.events()).extracting(GameEvent::type).contains("DISCARDED");
        // 假人轮转后真人再次摸牌：快照里的 TING_INFO 必须按新的 14 张手牌重算，
        // 已打出的牌（baseDealPrefix 手牌无重复）不得残留为可打候选。
        JsonNode state = engine.sessionState(step.table(), QaRoundTestRigs.humanDealerContext());
        JsonNode tingMahs = state.path("tingInfosBySeat").path("1").path("tingMahs");
        assertThat(tingMahs.isArray()).isTrue();
        assertThat(step.table().hands().get(1)).doesNotContain(tile);
        tingMahs.forEach(
                entry -> {
                    int discard = entry.path("discard").asInt();
                    assertThat(discard).isNotEqualTo(tile);
                    assertThat(step.table().hands().get(1)).contains(discard);
                    assertThat(entry.path("huTargets").isArray()).isTrue();
                });
    }

    @Test
    void tingInfoSurvivesTheStateCodecRoundTrip() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep result =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaTaizhouRoundEngineTest.baseDealPrefix());
        JsonNode state = engine.sessionState(result.table(), QaRoundTestRigs.humanDealerContext());

        QaRoundTable restored = engine.readTable(state);
        JsonNode written = engine.sessionState(restored, QaRoundTestRigs.humanDealerContext());

        assertThat(written.path("tingInfosBySeat").toString())
                .isEqualTo(state.path("tingInfosBySeat").toString());
        assertThat(written.path("shengPaiCount").asInt())
                .isEqualTo(state.path("shengPaiCount").asInt());
        assertThat(written.path("leftBankerCount").asInt())
                .isEqualTo(state.path("leftBankerCount").asInt());
    }

    @Test
    void shengPaiIsAbsentImmediatelyAfterTheOpeningDeal()
            throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaRoundStep result =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaTaizhouRoundEngineTest.baseDealPrefix());

        List<GameEvent> events = result.events();
        assertThat(events).extracting(GameEvent::type).doesNotContain("SHENG_PAI_COUNT");
        JsonNode state = engine.sessionState(result.table(), QaRoundTestRigs.humanDealerContext());
        assertThat(state.path("shengPaiCount").isNull()).isTrue();
    }

    @Test
    void shengPaiCountNeverDropsBelowTheYellowThresholdAcrossAFullAllBotRound() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaTaizhouRoundResult result =
                engine.start(
                        QaTaizhouRoundEngineTest.request(
                                QaTaizhouRoundEngineTest.seats(true, true, true, true)),
                        QaTaizhouRoundEngineTest.baseDealPrefix());

        result.events().stream()
                .filter(event -> event.type().equals("SHENG_PAI_COUNT"))
                .forEach(
                        event ->
                                assertThat((Integer) event.payload().get("shengPaiCount"))
                                        .isBetween(17, 31));
    }

    @Test
    void leftBankerOfEightArrivesOncePerRoundAfterMultipleChoiceStarted() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);

        QaRoundStep result =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaTaizhouRoundEngineTest.baseDealPrefix());

        List<GameEvent> leftBanker =
                result.events().stream()
                        .filter(event -> event.type().equals("LEFT_BANKER"))
                        .toList();
        assertThat(leftBanker).hasSize(1);
        assertThat(leftBanker.get(0).audience()).isEqualTo(GameEvent.Audience.PUBLIC);
        assertThat(leftBanker.get(0).payload()).containsEntry("leftBankerCount", 8);
        assertThat(indexOf(result.events(), "LEFT_BANKER"))
                .isGreaterThan(indexOf(result.events(), "MULTIPLE_CHOICE_CHANGED"))
                .isLessThan(indexOf(result.events(), "DEALT"));
        JsonNode state = engine.sessionState(result.table(), QaRoundTestRigs.humanDealerContext());
        assertThat(state.path("leftBankerCount").asInt()).isEqualTo(8);
    }

    private static int indexOf(List<GameEvent> events, String type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).type().equals(type)) {
                return index;
            }
        }
        throw new AssertionError("no event of type " + type);
    }
}
