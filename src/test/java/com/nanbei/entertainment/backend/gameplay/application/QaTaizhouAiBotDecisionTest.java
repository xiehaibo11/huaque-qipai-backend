package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouAiBotDecisionTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TARGET = 0x25;

    @Test
    void rejectsAnAiActionOutsideTheServerLegalSet() {
        QaRoundTable table = table(Set.of(1));
        table.activeSeat = 1;
        table.hands().get(1).addAll(List.of(0x11, 0x12, 0x39));
        QaTaizhouBotPolicy policy =
                new QaTaizhouBotPolicy((ignored, legalActions) -> Optional.of("DISCARD:153"));

        QaTaizhouBotPolicy.Decision decision = policy.decideTurn(table, 1);

        assertThat(decision.action()).isEqualTo(QaTaizhouBotPolicy.Action.DISCARD);
        assertThat(decision.tile()).isEqualTo(0x39);
    }

    @Test
    void skipsTheAiRequestWhenPassIsTheOnlyLegalClaim() {
        QaRoundTable table = table(Set.of(2));
        table.hands().get(2).addAll(List.of(0x11, 0x14, 0x19));
        AtomicBoolean requested = new AtomicBoolean();
        QaTaizhouBotPolicy policy =
                new QaTaizhouBotPolicy(
                        (ignored, legalActions) -> {
                            requested.set(true);
                            return Optional.of("PASS");
                        });

        QaTaizhouBotPolicy.Decision decision = policy.decideDiscardClaim(table, 2, 1, TARGET);

        assertThat(decision.action()).isEqualTo(QaTaizhouBotPolicy.Action.PASS);
        assertThat(requested).isFalse();
    }

    @Test
    void doesNotRequestRemoteAiOutsideTheQaGoldRoom() {
        QaRoundTable table = table(Set.of(1));
        table.goldMode = false;
        table.activeSeat = 1;
        table.hands().get(1).addAll(List.of(0x11, 0x12, 0x39));
        AtomicBoolean requested = new AtomicBoolean();
        QaTaizhouBotPolicy policy =
                new QaTaizhouBotPolicy(
                        (ignored, legalActions) -> {
                            requested.set(true);
                            return Optional.of(legalActions.getFirst());
                        });

        policy.decideTurn(table, 1);

        assertThat(requested).isFalse();
    }

    @Test
    void offersBothPungAndExposedKongWhenTheBotHoldsThreeMatchingTiles() {
        QaRoundTable table = table(Set.of(2));
        table.hands().get(2).addAll(List.of(TARGET, TARGET, TARGET, 0x11));
        AtomicReference<List<String>> offered = new AtomicReference<>();
        QaTaizhouBotPolicy policy =
                new QaTaizhouBotPolicy(
                        (ignored, legalActions) -> {
                            offered.set(legalActions);
                            return Optional.of("PUNG:37");
                        });

        QaTaizhouBotPolicy.Decision decision =
                policy.decideDiscardClaim(table, 2, 1, TARGET);

        assertThat(offered.get())
                .contains("KONG:EXPOSED:37", "PUNG:37", "PASS");
        assertThat(decision.action()).isEqualTo(QaTaizhouBotPolicy.Action.PUNG);
    }

    @Test
    void humanOfferIncludesBothPungAndExposedKong() {
        QaRoundTable table = table(Set.of());
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        table.activeSeat = 1;
        table.hands().get(1).add(TARGET);
        table.hands().get(2).addAll(List.of(TARGET, TARGET, TARGET, 0x11));
        QaRoundTurnDriver driver = driver(new QaTaizhouBotPolicy());

        driver.discard(table, context(), 2L, new java.util.ArrayList<>(), 1, TARGET);

        int mask = table.offers().get(2).powerMask;
        assertThat(mask & QaPowerMask.PUNG).isNotZero();
        assertThat(mask & QaPowerMask.MKONG).isNotZero();
    }

    @Test
    void aiBotCanPungAPlayersDiscard() {
        ClaimResult result = claimWithAiChoice("PUNG:37", List.of(TARGET, TARGET, 0x11));

        assertThat(result.table().melds().get(2))
                .contains(new QaRoundTable.Meld("PONG", List.of(TARGET, TARGET, TARGET), 1));
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence("MELD_APPLIED", "TURN_ADVANCED", "DISCARDED");
        GameEvent turn =
                result.events().stream()
                        .filter(event -> event.type().equals("TURN_ADVANCED"))
                        .findFirst()
                        .orElseThrow();
        long delay = ((Number) turn.payload().get("playbackDelayMillis")).longValue();
        assertThat(turn.payload().get("clockRemainingSeconds"))
                .isEqualTo((int) ((delay + 999L) / 1_000L));
    }

    @Test
    void aiBotCanChowThePreviousPlayersDiscard() {
        ClaimResult result = claimWithAiChoice("CHOW:0", List.of(0x26, 0x27, 0x11));

        assertThat(result.table().melds().get(2))
                .contains(new QaRoundTable.Meld("CHOW", List.of(TARGET, 0x26, 0x27), 1));
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence("MELD_APPLIED", "TURN_ADVANCED", "DISCARDED");
    }

    @Test
    void aiBotCanKongAPlayersDiscard() {
        ClaimResult result =
                claimWithAiChoice("KONG:EXPOSED:37", List.of(TARGET, TARGET, TARGET, 0x11));

        assertThat(result.table().melds().get(2))
                .contains(
                        new QaRoundTable.Meld(
                                "EXPOSED_KONG",
                                List.of(TARGET, TARGET, TARGET, TARGET),
                                1));
        assertThat(result.events())
                .extracting(GameEvent::type)
                .containsSubsequence("MELD_APPLIED", "DRAWN", "TURN_ADVANCED", "DISCARDED");
    }

    @Test
    void aiBotCanDeclareAConcealedKongOnItsTurn() {
        QaRoundTable table = table(Set.of(1));
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        table.activeSeat = 1;
        table.hands().get(1).addAll(List.of(TARGET, TARGET, TARGET, TARGET, 0x11));
        QaTaizhouBotPolicy policy = policyChoosing("KONG:CONCEALED:37");
        QaRoundTurnDriver driver = driver(policy);
        List<GameEvent> events = new java.util.ArrayList<>();

        driver.offerOrBotPlay(table, context(), 2L, events, TARGET);

        assertThat(table.melds().get(1))
                .contains(
                        new QaRoundTable.Meld(
                                "CONCEALED_KONG",
                                List.of(TARGET, TARGET, TARGET, TARGET),
                                1));
        assertThat(events)
                .extracting(GameEvent::type)
                .containsSubsequence("MELD_APPLIED", "DRAWN", "TURN_ADVANCED", "DISCARDED");
    }

    @Test
    void aiBotFillKongStartsANewThinkingCountdownAfterTheReplacementDraw() {
        QaRoundTable table = table(Set.of(1));
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        table.activeSeat = 1;
        table.hands().get(1).addAll(List.of(TARGET, 0x11));
        QaRoundTable.Meld pong =
                new QaRoundTable.Meld("PONG", List.of(TARGET, TARGET, TARGET), 2);
        table.melds().get(1).add(pong);
        QaRoundTurnDriver driver = driver(policyChoosing("KONG:FILL:37"));
        List<GameEvent> events = new java.util.ArrayList<>();

        driver.offerOrBotPlay(table, context(), 2L, events, TARGET);
        driver.adjudicate(table, context(), 2L, events);

        assertThat(events)
                .extracting(GameEvent::type)
                .containsSubsequence("MELD_APPLIED", "DRAWN", "TURN_ADVANCED", "DISCARDED");
        assertThat(table.melds().get(1))
                .contains(
                        new QaRoundTable.Meld(
                                "FILL_KONG",
                                List.of(TARGET, TARGET, TARGET, TARGET),
                                1));
    }

    private static ClaimResult claimWithAiChoice(String choice, List<Integer> botHand) {
        QaRoundTable table = table(Set.of(2));
        table.stage = QaRoundTable.Stage.AWAIT_PLAY;
        table.activeSeat = 1;
        table.hands().get(1).add(TARGET);
        table.hands().get(2).addAll(botHand);
        QaRoundTurnDriver driver = driver(policyChoosing(choice));
        List<GameEvent> events = new java.util.ArrayList<>();

        driver.discard(table, context(), 2L, events, 1, TARGET);
        driver.adjudicate(table, context(), 2L, events);

        return new ClaimResult(table, events);
    }

    private static QaTaizhouBotPolicy policyChoosing(String preferred) {
        return new QaTaizhouBotPolicy(
                (ignored, legalActions) ->
                        legalActions.contains(preferred)
                                ? Optional.of(preferred)
                                : legalActions.stream()
                                        .filter(action -> action.startsWith("DISCARD:"))
                                        .findFirst());
    }

    private static QaRoundTurnDriver driver(QaTaizhouBotPolicy policy) {
        QaTaizhouProjection projection = new QaTaizhouProjection(OBJECT_MAPPER);
        return new QaRoundTurnDriver(
                new QaRoundEventFactory(projection, TaizhouRoundMode.QA),
                policy,
                new QaTingInfoCalculator());
    }

    private static QaRoundTable table(Set<Integer> botSeats) {
        QaRoundTable table = QaRoundTable.newRound(4, 1, 1, botSeats);
        table.goldMode = true;
        table.wall.addAll(java.util.Collections.nCopies(32, 0x19));
        table.jokerRule = QaTaizhouJokerRule.synthetic();
        return table;
    }

    private static QaRoundContext context() {
        return new QaRoundContext(
                "123456",
                "不平搓/不封顶",
                QaTaizhouRoundEngineTest.seats(false, true, true, true),
                QaRoundTestRigs.NOW);
    }

    private record ClaimResult(QaRoundTable table, List<GameEvent> events) {}
}
