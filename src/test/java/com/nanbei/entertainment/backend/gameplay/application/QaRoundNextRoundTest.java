package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NEXT_ROUND（南北自建 QA 多局流转，非原版服务端算法）：
 * 局终后任何座位可开启下一局，新确定性 seed 派生墙，roundNumber+1，
 * LEFT_BANKER 递减、生牌数重置 22、比分由会话座位累积。
 */
class QaRoundNextRoundTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void nextRoundDealsAFreshRoundWithIncrementedNumberAndDecrementedLeftBanker() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaTaizhouRoundResult roundOne =
                engine.start(
                        QaTaizhouRoundEngineTest.request(
                                QaTaizhouRoundEngineTest.seats(true, true, true, true)),
                        QaTaizhouRoundEngineTest.baseDealPrefix());
        assertThat(roundOne.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        QaRoundTable finished = engine.readTable(roundOne.state());

        // 自建简化：任何座位成员都可发起 NEXT_ROUND（这里用 3 号位）。
        QaRoundStep step =
                engine.apply(finished, QaRoundTestRigs.allBotContext(), 3,
                        GameplayCommandType.NEXT_ROUND, null, 2L);

        assertThat(step.table().roundNumber).isEqualTo(2);
        assertThat(step.events())
                .extracting(GameEvent::type)
                .containsSubsequence(
                        "BOT_SEATS_FILLED",
                        "WALL_SHUFFLED",
                        "MULTIPLE_CHOICE_STARTED",
                        "LEFT_BANKER",
                        "DEALT",
                        "SHENG_PAI_COUNT");
        List<GameEvent> leftBanker =
                step.events().stream()
                        .filter(event -> event.type().equals("LEFT_BANKER"))
                        .toList();
        assertThat(leftBanker).hasSize(1);
        assertThat(leftBanker.get(0).payload()).containsEntry("leftBankerCount", 7);
        List<GameEvent> shengPai =
                step.events().stream()
                        .filter(event -> event.type().equals("SHENG_PAI_COUNT"))
                        .toList();
        assertThat(shengPai).hasSize(1);
        assertThat(shengPai.get(0).payload()).containsEntry("shengPaiCount", 22);

        JsonNode state = engine.sessionState(step.table(), QaRoundTestRigs.allBotContext());
        assertThat(state.path("roundNumber").asInt()).isEqualTo(2);
        assertThat(state.path("leftBankerCount").asInt()).isEqualTo(7);
        assertThat(state.path("settlement").isNull()).isFalse();
    }

    @Test
    void nextRoundIsDeterministicForTheSameSessionCursor() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaTaizhouRoundResult roundOne =
                engine.start(
                        QaTaizhouRoundEngineTest.request(
                                QaTaizhouRoundEngineTest.seats(true, true, true, true)),
                        QaTaizhouRoundEngineTest.baseDealPrefix());

        QaRoundStep first =
                engine.apply(engine.readTable(roundOne.state()),
                        QaRoundTestRigs.allBotContext(), 1,
                        GameplayCommandType.NEXT_ROUND, null, 2L);
        QaRoundStep second =
                engine.apply(engine.readTable(roundOne.state()),
                        QaRoundTestRigs.allBotContext(), 1,
                        GameplayCommandType.NEXT_ROUND, null, 2L);

        assertThat(first.table().wall).isEqualTo(second.table().wall);
        assertThat(first.events().stream().map(GameEvent::type).toList())
                .isEqualTo(second.events().stream().map(GameEvent::type).toList());
    }

    @Test
    void nextRoundWhilePlayingIsRejected() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep playing =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaTaizhouRoundEngineTest.baseDealPrefix());
        JsonNode state = engine.sessionState(playing.table(), QaRoundTestRigs.humanDealerContext());
        assertThat(QaTaizhouRoundEngine.phaseOf(playing.table())).isEqualTo(GamePhase.PLAYING);

        assertThatThrownBy(
                        () ->
                                engine.apply(engine.readTable(state),
                                        QaRoundTestRigs.humanDealerContext(), 1,
                                        GameplayCommandType.NEXT_ROUND, null, 3L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }
}
