package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class QaTaizhouRoundKongHuCommandTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void anExposedKongClaimsTheDiscardAndDrawsAReplacement() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(
                        engine, QaRoundTestRigs.exposedKongWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 2);
        assertThat((Integer) offer.get("powerMask") & QaPowerMask.MKONG).isNotZero();
        assertThat(offer.get("kongOptions"))
                .isEqualTo(List.of(Map.of("kongType", "EXPOSED", "tileValue", 0x25)));

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.KONG,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":37,\"kongType\":\"EXPOSED\",\"actionToken\":\""
                                        + offer.get("actionToken") + "\"}"),
                        3L);

        assertThat(step.events()).extracting(GameEvent::type)
                .containsExactly("MELD_APPLIED", "DRAWN", "DRAWN", "ACTION_OFFERED", "TING_INFO");
        Map<String, Object> meld = step.events().get(0).payload();
        assertThat(meld)
                .containsEntry("seat", 2)
                .containsEntry("combType", "EXPOSED_KONG")
                .containsEntry("fromSeat", 1);
        assertThat(meld.get("tiles")).isEqualTo(List.of(0x25, 0x25, 0x25, 0x25));
        assertThat(started.table().rivers().get(1)).isEmpty();
    }

    @Test
    void aConcealedKongFromThePlayersOwnDrawUsesTheKongOption() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaRoundTestRigs.concealedKongWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 1);
        assertThat((Integer) offer.get("powerMask") & QaPowerMask.CKONG).isNotZero();
        assertThat(offer.get("kongOptions"))
                .isEqualTo(List.of(Map.of("kongType", "CONCEALED", "tileValue", 0x37)));

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.KONG,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":55,\"kongType\":\"CONCEALED\",\"actionToken\":\""
                                        + offer.get("actionToken") + "\"}"),
                        3L);

        assertThat(step.events()).extracting(GameEvent::type)
                .containsExactly("MELD_APPLIED", "DRAWN", "DRAWN", "ACTION_OFFERED", "TING_INFO");
        Map<String, Object> meld = step.events().get(0).payload();
        assertThat(meld)
                .containsEntry("seat", 1)
                .containsEntry("combType", "CONCEALED_KONG")
                .containsEntry("fromSeat", 1);
        assertThat(meld.get("tiles")).isEqualTo(List.of(0x37, 0x37, 0x37, 0x37));
        assertThat(started.table().hands().get(1)).doesNotContain(0x37);
    }

    @Test
    void aFillKongUpgradesAnExistingPong() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(engine, QaRoundTestRigs.pungWall());
        Map<String, Object> pungOffer = QaRoundTestRigs.lastOffer(started.events(), 2);
        QaRoundStep pungStep =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.PUNG,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":37,\"actionToken\":\""
                                        + pungOffer.get("actionToken") + "\"}"),
                        3L);
        // 碰后由引擎发新的出牌 offer；测试注入第四张 0x25 与补杠选项，模拟摸到第四张。
        started.table().hands().get(2).add(0x25);
        Map<String, Object> playOfferPayload = pungStep.events().get(1).payload();
        QaRoundTable.PendingOffer playOffer = started.table().offers().get(2);
        started.table()
                .offers()
                .put(
                        2,
                        new QaRoundTable.PendingOffer(
                                playOffer.offerId,
                                playOffer.actionToken,
                                QaPowerMask.PLAY | QaPowerMask.TKONG,
                                0x25,
                                List.of(),
                                new java.util.ArrayList<>(
                                        List.of(new QaMeldCandidates.KongOption("FILL", 0x25))),
                                2,
                                true));

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.KONG,
                        OBJECT_MAPPER.readTree(
                                "{\"tileValue\":37,\"kongType\":\"FILL\",\"actionToken\":\""
                                        + playOfferPayload.get("actionToken") + "\"}"),
                        4L);

        assertThat(step.events()).extracting(GameEvent::type)
                .containsExactly("MELD_APPLIED", "DRAWN", "DRAWN", "ACTION_OFFERED", "TING_INFO");
        Map<String, Object> meld = step.events().get(0).payload();
        assertThat(meld)
                .containsEntry("seat", 2)
                .containsEntry("combType", "FILL_KONG")
                .containsEntry("fromSeat", 2);
        assertThat(started.table().melds().get(2))
                .containsExactly(
                        new QaRoundTable.Meld("FILL_KONG", List.of(0x25, 0x25, 0x25, 0x25), 2));
        assertThat(started.table().hands().get(2)).doesNotContain(0x25);
    }

    @Test
    void aSelfDrawnHuEndsTheRoundAsZimo() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.humanDealerAfterMultipleChoice(
                        engine, QaRoundTestRigs.selfHuWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 1);
        assertThat((Integer) offer.get("powerMask") & QaPowerMask.HU).isNotZero();

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.humanDealerContext(),
                        1,
                        GameplayCommandType.HU,
                        OBJECT_MAPPER.readTree(
                                "{\"actionToken\":\"" + offer.get("actionToken") + "\"}"),
                        3L);

        assertThat(step.roundFinished()).isTrue();
        assertThat(step.events()).extracting(GameEvent::type)
                .containsExactly("WIN_DECLARED", "SCORES_SETTLED", "ROUND_RESULT_READY");
        Map<String, Object> win = step.events().get(0).payload();
        assertThat(win)
                .containsEntry("winnerSeat", 1)
                .containsEntry("winType", "ZIMO")
                .containsEntry("endPlayerState", "EPS_HU");
        assertThat(step.scoreDeltasBySeat())
                .containsEntry(1, 84L)
                .containsEntry(2, -28L)
                .containsEntry(3, -28L)
                .containsEntry(4, -28L);
    }

    @Test
    void aHuOnADiscardEndsTheRoundAsDianpao() throws Exception {
        QaTaizhouRoundEngine engine = new QaTaizhouRoundEngine(OBJECT_MAPPER);
        QaRoundStep started =
                QaRoundTestRigs.botDealerAfterMultipleChoice(
                        engine, QaRoundTestRigs.discardHuWall());
        Map<String, Object> offer = QaRoundTestRigs.lastOffer(started.events(), 2);
        assertThat((Integer) offer.get("powerMask") & QaPowerMask.HU).isNotZero();

        QaRoundStep step =
                engine.apply(
                        started.table(),
                        QaRoundTestRigs.botDealerContext(),
                        2,
                        GameplayCommandType.HU,
                        OBJECT_MAPPER.readTree(
                                "{\"actionToken\":\"" + offer.get("actionToken") + "\"}"),
                        3L);

        assertThat(step.roundFinished()).isTrue();
        Map<String, Object> win = step.events().get(0).payload();
        assertThat(win)
                .containsEntry("winnerSeat", 2)
                .containsEntry("winType", "DIANPAO")
                .containsEntry("endPlayerState", "EPS_HU")
                .containsEntry("discarderSeat", 1);
        assertThat(step.scoreDeltasBySeat())
                .containsEntry(1, -20L)
                .containsEntry(2, 40L)
                .containsEntry(3, -10L)
                .containsEntry(4, -10L);
        JsonNode originalMsgResult =
                engine.sessionState(step.table(), QaRoundTestRigs.botDealerContext())
                        .path("settlement")
                        .path("originalMsgResult");
        assertThat(originalMsgResult.path("XY_ID").asInt()).isEqualTo(1026);
        assertThat(jsonIntList(originalMsgResult.path("nWinLost")))
                .containsExactly(-20, 40, -10, -10);
        assertThat(jsonIntList(originalMsgResult.path("nPlayerState")))
                .containsExactly(2, 1, 0, 0);
        assertThat(originalMsgResult.path("nDanFang").asInt()).isEqualTo(0x39);
        assertThat(originalMsgResult.path("bFinal").asBoolean()).isFalse();
        assertThat(originalMsgResult.path("bFengDing")).hasSize(4);
    }

    private static List<Integer> jsonIntList(JsonNode node) {
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        for (JsonNode value : node) {
            values.add(value.asInt());
        }
        return values;
    }
}
