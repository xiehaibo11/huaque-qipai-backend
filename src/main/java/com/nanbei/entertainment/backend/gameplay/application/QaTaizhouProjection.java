package com.nanbei.entertainment.backend.gameplay.application;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * QA 台州回合的可见区投影（南北自建测试投影，非原版服务端算法）。
 * 形状沿用旧脚本引擎 {@code QaMahjongRoundProjection} 的 visibleRound/publicRound/settlement
 * 契约，并补充副露（melds）、出牌权限（playPermissionsBySeat）等桌面状态。30109 大众
 * 台州麻将为 136 张无花牌玩法，快照里的 flowers 固定为空。
 */
final class QaTaizhouProjection {
    private static final DateTimeFormatter SETTLE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;

    QaTaizhouProjection(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    JsonNode visibleRoundsBySeat(QaRoundContext context, QaRoundTable table) {
        Map<String, JsonNode> bySeat = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            bySeat.put(Integer.toString(seat), visibleRound(context, table, seat));
        }
        return node(bySeat);
    }

    JsonNode visibleRound(QaRoundContext context, QaRoundTable table, int viewerSeat) {
        List<Map<String, Object>> handPayload = new ArrayList<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seatNumber", seat);
            List<Integer> seatHand = table.hands().get(seat);
            List<Integer> concealed = concealedTiles(table, seat, seatHand);
            entry.put(
                    "concealedTiles",
                    seat == viewerSeat ? concealed : backs(concealed.size()));
            if (table.hasDrawnTile(seat)) {
                entry.put(
                        "drawnTile",
                        seat == viewerSeat ? table.drawnTile : QaTaizhouTiles.BACK);
            }
            List<Map<String, Object>> meldPayload = new ArrayList<>();
            for (QaRoundTable.Meld meld : table.melds().get(seat)) {
                meldPayload.add(meldPayload(meld));
            }
            entry.put("meldCount", meldPayload.size());
            entry.put("melds", meldPayload);
            entry.put("flowers", List.of());
            handPayload.add(entry);
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chairCount", table.chairCount);
        value.put("mySeat", viewerSeat);
        // 庄家座位：中心转向盘按原版 rotateWindPos 整块旋转所需（Module.luac:870-875）。
        value.put("dealerSeat", table.dealerSeat);
        value.put("hands", handPayload);
        value.put("jokerTiles", table.jokerRule.jokerTiles());
        value.put("insteadTiles", table.jokerRule.insteadTiles());
        value.put("openTiles", List.copyOf(table.openTiles()));
        value.put("rivers", riverPayload(table));
        if (table.lastDiscard != null) {
            value.put(
                    "lastDiscard",
                    Map.of(
                            "seatNumber",
                            table.lastDiscard.seat(),
                            "tileIndex",
                            table.lastDiscard.tileIndex()));
        }
        return node(value);
    }

    JsonNode publicRound(QaRoundContext context, QaRoundTable table) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chairCount", table.chairCount);
        value.put("jokerTiles", table.jokerRule.jokerTiles());
        value.put("insteadTiles", table.jokerRule.insteadTiles());
        value.put("openTiles", List.copyOf(table.openTiles()));
        value.put("rivers", riverPayload(table));
        if (table.lastDiscard != null) {
            value.put(
                    "lastDiscard",
                    Map.of(
                            "seatNumber",
                            table.lastDiscard.seat(),
                            "tileIndex",
                            table.lastDiscard.tileIndex()));
        }
        return node(value);
    }

    /**
     * 加倍选择层只在金币场出现。
     *
     * <p>原版 {@code app/Config/GameSub.lua:104} 的房卡台州麻将 30109 是
     * {@code IsGoldMode = "BOTNo"}，{@code :140} 的金币场 30400 才是 {@code "BOTYes"}
     * （且 {@code DefaultBoxGameId = 30109}）。两者的牌局都跑在 30109 引擎上，
     * 因此不能用 gameId 区分，只能看房间所属场所。
     *
     * <p>其余展示值仍是 QA fixture。
     */
    JsonNode multipleChoice(QaRoundContext context, QaRoundTable table) {
        List<Map<String, Object>> seatChoices = new ArrayList<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : context.seats()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seatNumber", seat.seatNumber());
            entry.put("choice", table.choices().get(seat.seatNumber()));
            seatChoices.add(entry);
        }
        return node(
                Map.of(
                        "goldMode", context.goldMode(),
                        "choiceActive", !QaRoundFlowAdvance.allMultipleChoicesMade(context, table),
                        "baseScore", 60,
                        "currentMultiplier", 1,
                        "cardUseCount", 1,
                        "diamondUseCount", 50,
                        "mySeat", 1,
                        "allowedChoices", List.of("PASS", "DEFAULT", "SUPER"),
                        "seatChoices", seatChoices));
    }

    JsonNode diceRoll(QaRoundTable table) {
        return node(diceRollPayload(table.diceRoll));
    }

    static Map<String, Object> diceRollPayload(QaRoundTable.DiceRoll diceRoll) {
        Objects.requireNonNull(diceRoll, "diceRoll");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nSeat", diceRoll.seatNumber());
        payload.put("nCount", diceRoll.values().size());
        payload.put("nChips", List.copyOf(diceRoll.values()));
        payload.put("showAni", diceRoll.showAni());
        payload.put("gameStep", diceRoll.gameStep());
        return payload;
    }

    /**
     * 吃碰杠胡待答窗口按座位下发，形状与 {@code ACTION_OFFERED} 事件一致，供快照恢复动作条。
     * 自摸时同一窗口可同时含 PLAY 与 HU/杠；出牌索引仍由
     * {@link #playPermissionsBySeat} 下发，但动作 offer 不能因此被快照丢弃。
     */
    JsonNode actionOffersBySeat(QaRoundTable table) {
        Map<String, Object> bySeat = new LinkedHashMap<>();
        for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry : table.offers().entrySet()) {
            QaRoundTable.PendingOffer offer = entry.getValue();
            if (offer.answered()
                    || (offer.playOffer && (offer.powerMask & ~QaPowerMask.PLAY) == 0)) {
                continue;
            }
            bySeat.put(
                    Integer.toString(entry.getKey()),
                    actionOfferPayload(entry.getKey(), offer));
        }
        return node(bySeat);
    }

    /** 快照级平铺副露列表（含 seat 字段），形状与 {@code MELD_APPLIED} 事件一致。 */
    JsonNode meldsFlat(QaRoundTable table) {
        List<Map<String, Object>> payload = new ArrayList<>();
        table.melds()
                .forEach(
                        (seat, seatMelds) -> {
                            for (QaRoundTable.Meld meld : seatMelds) {
                                Map<String, Object> entry = new LinkedHashMap<>(meldPayload(meld));
                                entry.put("seat", seat);
                                payload.add(entry);
                            }
                        });
        return node(payload);
    }

    /** 30109 大众玩法无花牌；快照级花牌列表固定为空，避免旧通用状态投影出补花区。 */
    JsonNode flowersFlat(QaRoundTable table) {
        return node(List.of());
    }

    /** 快照级听牌映射（含 seat 字段），形状与 {@code TING_INFO} 事件一致。 */
    JsonNode tingInfosBySeat(QaRoundTable table) {
        Map<String, Object> bySeat = new LinkedHashMap<>();
        table.tingInfos()
                .forEach(
                        (seat, entries) ->
                                bySeat.put(
                                        Integer.toString(seat), tingInfoPayload(seat, entries)));
        return node(bySeat);
    }

    /** {@code TING_INFO} 事件与快照 {@code tingInfosBySeat} 共用的载荷形状（自建）。 */
    static Map<String, Object> tingInfoPayload(int seat, List<QaRoundTable.TingEntry> entries) {
        List<Map<String, Object>> tingMahs = new ArrayList<>();
        for (QaRoundTable.TingEntry entry : entries) {
            Map<String, Object> ting = new LinkedHashMap<>();
            ting.put("discard", entry.discard());
            ting.put("huTargets", entry.huTargets());
            tingMahs.add(ting);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seat", seat);
        payload.put("tingMahs", tingMahs);
        return payload;
    }

    /** {@code ACTION_OFFERED} 事件与快照 {@code actionOffersBySeat} 共用的载荷形状。 */
    static Map<String, Object> actionOfferPayload(int seat, QaRoundTable.PendingOffer offer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seat", seat);
        payload.put("activeSeat", seat);
        payload.put("clockRemainingSeconds", QaRoundClock.DEFAULT_SECONDS);
        payload.put("powerMask", offer.powerMask);
        payload.put("actionToken", offer.actionToken);
        payload.put("contextTile", offer.contextTile);
        payload.put("chowCandidates", offer.chowCandidates);
        List<Map<String, Object>> kongOptions = new ArrayList<>();
        for (QaMeldCandidates.KongOption option : offer.kongOptions) {
            kongOptions.add(
                    Map.of("kongType", option.kongType(), "tileValue", option.tileValue()));
        }
        payload.put("kongOptions", kongOptions);
        payload.put("offerId", offer.offerId);
        return payload;
    }

    /** 出牌权限按 Android {@code GameplayRoundProtocol.parseOptionalPlayPermission} 的形状下发。 */
    JsonNode playPermissionsBySeat(QaRoundTable table) {
        return node(QaTaizhouPlayPermissions.bySeat(table));
    }

    static List<Integer> concealedTiles(
            QaRoundTable table, int seat, List<Integer> seatHand) {
        List<Integer> concealed = new ArrayList<>(seatHand);
        if (table.hasDrawnTile(seat)
                && !concealed.remove(Integer.valueOf(table.drawnTile))) {
            throw new IllegalStateException("drawn tile is absent from its hand");
        }
        return List.copyOf(concealed);
    }

    /** 结算投影：playerState 使用原版 EPS 数值并补充 endPlayerState 枚举名。 */
    JsonNode settlement(QaRoundContext context, QaRoundTable table) {
        Objects.requireNonNull(table.outcome, "outcome");
        QaRoundTable.Outcome outcome = table.outcome;
        QaTaizhouScorer.RoundScore score =
                outcome.winnerSeat() > 0
                        ? QaTaizhouScorer.score(
                                table,
                                outcome.winnerSeat(),
                                outcome.winType(),
                                outcome.discarderSeat(),
                                outcome.winningTile() == null
                                        ? QaTaizhouTiles.NO_TILE
                                        : outcome.winningTile())
                        : null;
        List<Map<String, Object>> seats = new ArrayList<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : context.seats()) {
            int seatNumber = seat.seatNumber();
            long delta = outcome.deltas().getOrDefault(seatNumber, 0L);
            String endState = outcome.endStates().getOrDefault(seatNumber, "EPS_NULL");
            QaTaizhouScorer.SeatScore seatScore =
                    score == null
                            ? QaTaizhouScorer.zeroSeat()
                            : score.seatScores()
                                    .getOrDefault(seatNumber, QaTaizhouScorer.zeroSeat());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seatNumber", seatNumber);
            entry.put("displayName", seat.displayName());
            entry.put("publicPlayerId", Long.toString(seat.publicPlayerId()));
            entry.put("wind", seatNumber);
            entry.put("banker", seatNumber == table.dealerSeat);
            entry.put("handHu", seatScore.handHu());
            entry.put("tai", seatScore.tai());
            entry.put("totalHu", seatScore.totalHu());
            entry.put("playerState", endPlayerStateValue(endState));
            entry.put("endPlayerState", endState);
            entry.put("fan", seatScore.fan());
            entry.put("gangScore", seatScore.gangScore());
            entry.put("total", delta);
            entry.put("delta", delta);
            entry.put("hasCaishen", seatScore.hasCaishen());
            entry.put("hasCaishenRestore", seatScore.hasCaishenRestore());
            // 原版 WinLost/ItemNode.luac:updateBG：settle_caishen_bg 行底图只在
            // 请财神道具剩余时间大于 0 时显示（CaiYunProp:isShowCaiYun），
            // 与手牌是否含财神无关；激活状态由 seatInputs 从财富状态表实时解析。
            entry.put("caishenPropActive", seat.caishenPropActive());
            List<Integer> handTiles = new ArrayList<>(table.hands().get(seatNumber));
            // 原版 WinLost/ItemMahsArea.luac:showResultMahs：胡牌张（dfMahID）从手牌
            // 抽出单独展示，自摸时该张仍在 hands 里，点炮时本就不在。
            boolean winner = seatNumber == outcome.winnerSeat();
            if (winner && outcome.winningTile() != null) {
                handTiles.remove(Integer.valueOf(outcome.winningTile()));
                entry.put("huTile", outcome.winningTile());
            }
            entry.put("handTiles", List.copyOf(handTiles));
            // 原版结算行把副露（combData）画在手牌左侧，缺失会让胡牌行缺牌。
            List<Map<String, Object>> seatMelds = new ArrayList<>();
            for (QaRoundTable.Meld meld : table.melds().get(seatNumber)) {
                Map<String, Object> meldEntry = meldPayload(meld);
                meldEntry.put("seat", seatNumber);
                seatMelds.add(meldEntry);
            }
            entry.put("melds", seatMelds);
            seats.add(entry);
        }
        return node(
                Map.of(
                        "result", outcome.winType(),
                        "roomNumber", context.roomNumber(),
                        "roundLabel", "第" + table.roundNumber + "局",
                        "time", SETTLE_TIME.format(context.occurredAt()),
                        "gameRule", context.gameRuleDisplay(),
                        "originalMsgResult", originalMsgResult1026(table, score),
                        "seats", seats));
    }

    Map<String, Object> totalResult(QaRoundTable table) {
        return QaTaizhouTotalResultProjection.project(table);
    }

    /**
     * JSON projection of Taizhou {@code msgResult}(XY_ID 1026). The recovered Lua reads
     * fixed 0..3 arrays, then 30109 WinLostData formats {@code nCountHu},
     * {@code nCountTai}, {@code nToTalCountHu} and {@code nPlayerState}.
     */
    private static Map<String, Object> originalMsgResult1026(
            QaRoundTable table, QaTaizhouScorer.RoundScore score) {
        List<Integer> nWinLost = zeroInts(4);
        List<Integer> nCountHu = zeroInts(4);
        List<Integer> nCountTai = zeroInts(4);
        List<Integer> nToTalCountHu = zeroInts(4);
        List<Integer> nPlayerState = zeroInts(4);
        List<Boolean> bFengDing = falseBooleans(4);
        boolean bLazi = false;
        for (int seat = 1; seat <= Math.min(table.chairCount, 4); seat++) {
            int index = seat - 1;
            QaTaizhouScorer.SeatScore seatScore =
                    score == null
                            ? QaTaizhouScorer.zeroSeat()
                            : score.seatScores().getOrDefault(seat, QaTaizhouScorer.zeroSeat());
            nWinLost.set(index, Math.toIntExact(table.outcome.deltas().getOrDefault(seat, 0L)));
            nCountHu.set(index, seatScore.handHu());
            nCountTai.set(index, seatScore.tai());
            nToTalCountHu.set(index, seatScore.totalHu());
            nPlayerState.set(
                    index,
                    endPlayerStateValue(
                            table.outcome.endStates().getOrDefault(seat, "EPS_NULL")));
            bLazi |= seatScore.totalHu() >= 100;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("XY_ID", 1026);
        payload.put("nWinLost", nWinLost);
        payload.put("nCountHu", nCountHu);
        payload.put("nCountTai", nCountTai);
        payload.put("nToTalCountHu", nToTalCountHu);
        payload.put("nPlayerState", nPlayerState);
        payload.put("bLazi", bLazi);
        payload.put("nDanFang", danFangTile(table));
        payload.put("bFinal", table.roundNumber >= table.maxPlayCount);
        payload.put("bFengDing", bFengDing);
        return payload;
    }

    private static List<Integer> zeroInts(int size) {
        List<Integer> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(0);
        }
        return values;
    }

    private static List<Boolean> falseBooleans(int size) {
        List<Boolean> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(false);
        }
        return values;
    }

    private static int danFangTile(QaRoundTable table) {
        if (table.outcome == null
                || table.outcome.discarderSeat() == null
                || table.lastDiscard == null) {
            return 0;
        }
        return table.lastDiscard.tile();
    }

    static int endPlayerStateValue(String endState) {
        return switch (endState) {
            case "EPS_HU" -> 1;
            case "EPS_DISCARD" -> 2;
            case "EPS_ROBKONG" -> 3;
            case "EPS_GANGSHANGKAIHUA" -> 4;
            case "EPS_CHENGBAO" -> 5;
            case "EPS_DRAWN" -> 9;
            default -> 0;
        };
    }

    static Map<String, Object> meldPayload(QaRoundTable.Meld meld) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("combType", meld.combType());
        payload.put("tiles", List.copyOf(meld.tiles()));
        payload.put("fromSeat", meld.fromSeat());
        return payload;
    }

    JsonNode node(Object value) {
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    /** 显式 JSON null（生产 ObjectMapper 为 non_null，Java null 的 Map 键会被丢弃）。 */
    JsonNode nullNode() {
        return objectMapper.nullNode();
    }

    private static List<Integer> backs(int count) {
        List<Integer> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(QaTaizhouTiles.BACK);
        }
        return result;
    }

    private static List<Map<String, Object>> riverPayload(QaRoundTable table) {
        int maxLineCount = table.chairCount == 2 ? 2 : 3;
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : table.rivers().entrySet()) {
            payload.add(
                    Map.of(
                            "seatNumber",
                            entry.getKey(),
                            "tiles",
                            List.copyOf(entry.getValue()),
                            "maxLineCount",
                            maxLineCount));
        }
        return payload;
    }
}
