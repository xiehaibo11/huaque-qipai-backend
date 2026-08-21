package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouRoundCommandTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void aDiscardConsumesTheOneTimeActionToken() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started = QaRoundTestRigs.humanDealerAfterMultipleChoice(engine);
        String token = QaRoundTestRigs.lastOfferToken(started.events(), 1);
        int tileValue = started.table().hands().get(1).get(0);

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.DISCARD,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":" + tileValue
                                        + ",\"actionToken\":\"" + token + "\"}"),
                        3L);

        assertThat(step.events()).extracting(GameEvent::type).startsWith("DISCARDED");
        assertThatThrownBy(
                        () ->
                                engine.apply(
                                        started.table(),
                                        QaRoundTestRigs.humanDealerContext(),
                                        1,
                                        GameplayCommandType.DISCARD,
                                        OBJECT_MAPPER.readTree(
                                                "{\"tileValue\":"
                                                        + started.table().hands().get(1).get(0)
                                                        + ",\"actionToken\":\""
                                                        + token
                                                        + "\"}"),
                                        4L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    @Test
    void aDiscardRejectsAnUnknownTokenAndATileOutsideTheHand() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started = QaRoundTestRigs.humanDealerAfterMultipleChoice(engine);

        assertThatThrownBy(
                        () ->
                                engine.apply(
                                        started.table(),
                                        QaRoundTestRigs.humanDealerContext(),
                                        1,
                                        GameplayCommandType.DISCARD,
                                        OBJECT_MAPPER.readTree(
                                                "{\"tileValue\":17,\"actionToken\":\"00000000-0000-0000-0000-000000000000\"}"),
                                        3L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
        int outsideTile = aTileNotInHand(started, 1);
        assertThatThrownBy(
                        () ->
                                engine.apply(
                                        started.table(),
                                        QaRoundTestRigs.humanDealerContext(),
                                        1,
                                        GameplayCommandType.DISCARD,
                                        OBJECT_MAPPER.readTree(
                                                "{\"tileValue\":" + outsideTile
                                                        + ",\"actionToken\":\""
                                                        + QaRoundTestRigs.lastOfferToken(
                                                                started.events(), 1)
                                                        + "\"}"),
                                        3L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    @Test
    void aChowAppliesTheCandidateAndRemovesTheDiscardFromTheRiver() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(engine, QaRoundTestRigs.chowWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 2);
        int powerMask = (Integer) offer.get("powerMask");
        assertThat(powerMask & QaPowerMask.CHOW).isNotZero();
        assertThat((List<?>) offer.get("chowCandidates"))
                .isEqualTo(List.of(List.of(0x37, 0x38, 0x39)));

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.CHOW,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":57,\"candidateIndex\":0,\"actionToken\":\""
                                        + offer.get("actionToken") + "\"}"),
                        3L);

        assertThat(step.events()).extracting(GameEvent::type)
                .containsExactly("MELD_APPLIED", "ACTION_OFFERED", "TING_INFO");
        Map<String, Object> meld = step.events().get(0).payload();
        assertThat(meld)
                .containsEntry("seat", 2)
                .containsEntry("combType", "CHOW")
                .containsEntry("fromSeat", 1);
        assertThat(meld.get("tiles")).isEqualTo(List.of(0x37, 0x38, 0x39));
        assertThat(started.table().rivers().get(1)).isEmpty();
        assertThat(started.table().melds().get(2))
                .containsExactly(new QaRoundTable.Meld("CHOW", List.of(0x37, 0x38, 0x39), 1));
        Map<String, Object> playOffer = step.events().get(1).payload();
        assertThat(playOffer.get("seat")).isEqualTo(2);
        assertThat((Integer) playOffer.get("powerMask") & QaPowerMask.PLAY).isNotZero();
    }

    @Test
    void aPungAppliesAPongMeld() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(engine, QaRoundTestRigs.pungWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 2);
        assertThat((Integer) offer.get("powerMask") & QaPowerMask.PUNG).isNotZero();

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.PUNG,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":37,\"actionToken\":\""
                                        + offer.get("actionToken") + "\"}"),
                        3L);

        Map<String, Object> meld = step.events().get(0).payload();
        assertThat(meld)
                .containsEntry("seat", 2)
                .containsEntry("combType", "PONG")
                .containsEntry("fromSeat", 1);
        assertThat(meld.get("tiles")).isEqualTo(List.of(0x25, 0x25, 0x25));
        assertThat(started.table().rivers().get(1)).isEmpty();
    }

    @Test
    void aHuWithoutAWinningHandIsRejected() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started = QaRoundTestRigs.humanDealerAfterMultipleChoice(engine);

        assertThatThrownBy(
                        () ->
                                engine.apply(
                                        started.table(),
                                        QaRoundTestRigs.humanDealerContext(),
                                        1,
                                        GameplayCommandType.HU,
                                        OBJECT_MAPPER.readTree(
                                                "{\"actionToken\":\""
                                                        + QaRoundTestRigs.lastOfferToken(
                                                                started.events(), 1)
                                                        + "\"}"),
                                        3L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAME_ACTION_NOT_ALLOWED);
    }

    @Test
    void aPassClosesTheOfferAndAdvancesTheTurn() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(engine, QaRoundTestRigs.pungWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 2);

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.PASS,
                        OBJECT_MAPPER.readTree(
                                "{\"actionToken\":\"" + offer.get("actionToken") + "\"}"),
                        3L);

        assertThat(step.events()).extracting(GameEvent::type)
                .startsWith("ACTION_EXPIRED")
                .contains("TURN_ADVANCED");
        Map<String, Object> expired = step.events().get(0).payload();
        assertThat(expired)
                .containsEntry("seat", 2)
                .containsEntry("offerId", offer.get("offerId"));
    }

    @Test
    void aMultipleChoiceCommandRecordsTheSeatChoice() {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaTaizhouRoundResult started = engine.start(QaRoundTestRigs.humanDealerRequest());

        assertThatThrownBy(
                        () ->
                                engine.apply(
                                        engine.start(QaRoundTestRigs.humanDealerRequest()).table(),
                                        QaRoundTestRigs.humanDealerContext(),
                                        1,
                                        GameplayCommandType.MULTIPLE_CHOICE,
                                        OBJECT_MAPPER.readTree("{\"choice\":\"DOUBLE\"}"),
                                        2L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.MULTIPLE_CHOICE,
                        OBJECT_MAPPER.readTree("{\"choice\":\"ADD\"}"),
                        2L);

        assertThat(step.events()).extracting(GameEvent::type)
                .containsSubsequence("MULTIPLE_CHOICE_CHANGED", "DEALT", "ACTION_OFFERED");
        assertThat(step.events().get(0).audience()).isEqualTo(GameEvent.Audience.PUBLIC);
        assertThat(started.table().choices()).containsEntry(1, "ADD");
    }

    @Test
    void theSessionStateExposesSnapshotActionOfferAndMeldsButFiltersDazhongFlowers()
            throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(engine, QaRoundTestRigs.chowWall());
        QaRoundStateCodec codec =
                new QaRoundStateCodec(OBJECT_MAPPER, new QaTaizhouProjection(OBJECT_MAPPER));
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 2);

        JsonNode pendingState = codec.sessionState(started.table(), QaRoundTestRigs.botDealerContext());
        assertThat(pendingState.path("melds")).isEmpty();
        assertThat(pendingState.path("flowers")).isEmpty();
        JsonNode pendingOffer = pendingState.path("actionOffersBySeat").path("2");
        assertThat(pendingOffer.path("actionToken").asText())
                .isEqualTo((String) offer.get("actionToken"));
        assertThat(pendingOffer.path("offerId").asInt()).isEqualTo((Integer) offer.get("offerId"));
        assertThat(pendingOffer.path("powerMask").asInt()).isEqualTo((Integer) offer.get("powerMask"));

        engine.apply(
                started.table(),
                QaRoundTestRigs.botDealerContext(),
                2,
                GameplayCommandType.CHOW,
                OBJECT_MAPPER.readTree(
                                "{\"tileValue\":57,\"candidateIndex\":0,\"actionToken\":\""
                                        + offer.get("actionToken") + "\"}"),
                3L);
        started.table().flowers().get(2).add(0x61);

        JsonNode state = codec.sessionState(started.table(), QaRoundTestRigs.botDealerContext());
        assertThat(state.path("actionOffersBySeat").path("2").isMissingNode()).isTrue();
        JsonNode meld = state.path("melds").get(0);
        assertThat(meld.path("seat").asInt()).isEqualTo(2);
        assertThat(meld.path("combType").asText()).isEqualTo("CHOW");
        assertThat(meld.path("fromSeat").asInt()).isEqualTo(1);
        assertThat(meld.path("tiles").get(0).asInt()).isEqualTo(0x37);
        assertThat(state.path("flowers")).isEmpty();
        state.path("visibleRoundsBySeat").forEach(visibleRound ->
                visibleRound.path("hands").forEach(hand ->
                        assertThat(hand.path("flowers")).isEmpty()));
    }

    private static int aTileNotInHand(QaRoundStep started, int seat) {
        for (int tile = 0x11; tile <= 0x53; tile++) {
            if (!started.table().hands().get(seat).contains(tile)
                    && QaTaizhouTiles.isPlayable(tile)) {
                return tile;
            }
        }
        throw new AssertionError("hand already contains every tile");
    }
}
