package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 回合状态与 {@code game_sessions.state} JSON 的互转。
 * 顶层保持 Android 快照已消费的字段（multipleChoice/visibleRoundsBySeat/playPermissionsBySeat/
 * actionOffersBySeat/melds/flowers/settlement/activeSeat/remainingWallCount），引擎私有状态放在 qaRound 节点。
 */
final class QaRoundStateCodec {
    private final ObjectMapper objectMapper;
    private final QaTaizhouProjection projection;
    private final TaizhouRoundMode mode;

    QaRoundStateCodec(ObjectMapper objectMapper, QaTaizhouProjection projection) {
        this(objectMapper, projection, TaizhouRoundMode.QA);
    }

    QaRoundStateCodec(
            ObjectMapper objectMapper,
            QaTaizhouProjection projection,
            TaizhouRoundMode mode) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    JsonNode sessionState(QaRoundTable table, QaRoundContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        mode.putMarkers(state);
        state.put("roundNumber", table.roundNumber);
        state.put("activeSeat", table.stage == QaRoundTable.Stage.ROUND_OVER ? null : table.activeSeat);
        state.put("clockRemainingSeconds", QaRoundClock.remainingSeconds(table));
        state.put("remainingWallCount", table.wall.size());
        state.put("shuffleAlgorithm", table.shuffleAlgorithm);
        state.put("shuffleSeedSource", table.shuffleSeedSource);
        state.put("shuffleCommitment", table.shuffleCommitment);
        state.put("wallState", wallStateNode(table));
        state.put(
                "diceRoll",
                table.diceRoll == null ? projection.nullNode() : projection.diceRoll(table));
        state.put(
                "multipleChoice",
                table.stage == QaRoundTable.Stage.AWAIT_MULTIPLE
                        ? projection.multipleChoice(context, table)
                        : null);
        state.put("visibleRoundsBySeat", projection.visibleRoundsBySeat(context, table));
        state.put("playPermissionsBySeat", projection.playPermissionsBySeat(table));
        state.put("actionOffersBySeat", projection.actionOffersBySeat(table));
        state.put("melds", projection.meldsFlat(table));
        state.put("flowers", projection.flowersFlat(table));
        state.put(
                "settlement",
                table.outcome == null ? null : projection.settlement(context, table));
        state.put(
                "totalResult",
                table.outcome != null && table.roundNumber >= table.maxPlayCount
                        ? projection.totalResult(table)
                        : null);
        state.put("shengPaiCount", table.shengPaiCount < 0 ? null : table.shengPaiCount);
        state.put("leftBankerCount", table.leftBankerCount < 0 ? null : table.leftBankerCount);
        state.put("tingInfosBySeat", projection.tingInfosBySeat(table));
        state.put("chengBaoFlagsBySeat", QaTaizhouBaoPai.flagsBySeat(table));
        state.put("qaRound", tableNode(table));
        return objectMapper.valueToTree(state);
    }

    QaRoundTable readTable(JsonNode sessionState) {
        JsonNode node = sessionState.path("qaRound");
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("session state has no qaRound node");
        }
        List<Integer> botSeats = new ArrayList<>();
        node.path("botSeats").forEach(seat -> botSeats.add(seat.asInt()));
        QaRoundTable table =
                QaRoundTable.newRound(
                        node.path("chairCount").asInt(),
                        node.path("dealerSeat").asInt(),
                        node.path("roundNumber").asInt(),
                        node.path("maxPlayCount").asInt(8),
                        botSeats);
        table.stage = QaRoundTable.Stage.valueOf(node.path("stage").asText());
        table.turnIndex = node.path("turnIndex").asInt();
        table.activeSeat = node.path("activeSeat").asInt();
        table.baseScore = node.path("baseScore").asLong(1L);
        table.goldMode = node.path("goldMode").asBoolean(false);
        node.path("openingCoinsBySeat")
                .properties()
                .forEach(
                        entry ->
                                table.openingCoinsBySeat.put(
                                        Integer.parseInt(entry.getKey()), entry.getValue().asLong()));
        table.shengPaiCount = node.path("shengPaiCount").asInt(-1);
        table.leftBankerCount = node.path("leftBankerCount").asInt(-1);
        node.path("discardedTileTypes")
                .forEach(tile -> table.discardedTileTypes.add(tile.asInt()));
        JsonNode discardSnapshot = node.path("lastDiscardSnapshot");
        if (discardSnapshot.isObject()) {
            table.lastDiscardSnapshot =
                    new QaRoundTable.DiscardSnapshot(
                            discardSnapshot.path("seat").asInt(),
                            discardSnapshot.path("tile").asInt(),
                            discardSnapshot.path("shengPaiStage").asBoolean(),
                            discardSnapshot.path("rawTile").asBoolean(),
                            discardSnapshot.path("allHandRaw").asBoolean());
        }
        table.baoPaiSeat =
                node.path("baoPaiSeat").isNumber()
                        ? node.path("baoPaiSeat").asInt()
                        : null;
        readSetMap(node.path("passedHuTiles"), table.passedHuTiles());
        readSetMap(node.path("passedPungTiles"), table.passedPungTiles());
        table.diceRoll = readDiceRoll(node.path("diceRoll"));
        node.path("wall").forEach(tile -> table.wall.add(tile.asInt()));
        table.shuffleAlgorithm =
                node.path("shuffleAlgorithm").asText(mode.wallAlgorithm());
        table.shuffleSeedSource =
                node.path("shuffleSeedSource").asText("LEGACY_UNRECORDED");
        table.shuffleCommitment =
                node.path("shuffleCommitment").isTextual()
                        ? node.path("shuffleCommitment").asText()
                        : null;
        table.physicalWallOpening = node.path("physicalWallOpening").asBoolean(false);
        table.wallOpenIndex = node.path("wallOpenIndex").asInt(-1);
        table.wallAsc = node.path("wallAsc").asInt(-1);
        table.wallDesc = node.path("wallDesc").asInt(-1);
        table.wallFirstAsc = node.path("wallFirstAsc").asInt(-1);
        table.wallFirstDesc = node.path("wallFirstDesc").asInt(-1);
        node.path("openTiles").forEach(tile -> table.openTiles().add(tile.asInt()));
        table.jokerRule =
                new QaTaizhouJokerRule(
                        node.path("jokerTile").asInt(),
                        node.path("insteadTile").asInt());
        table.drawnTileSeat = node.path("drawnTileSeat").asInt();
        table.drawnTile =
                node.path("drawnTile").isNumber() ? node.path("drawnTile").asInt() : null;
        readTileMap(node.path("hands"), table.hands());
        readTileMap(node.path("rivers"), table.rivers());
        readTileMap(node.path("flowers"), table.flowers());
        JsonNode melds = node.path("melds");
        melds.properties()
                .forEach(
                        entry -> {
                            List<QaRoundTable.Meld> seatMelds =
                                    table.melds().get(Integer.parseInt(entry.getKey()));
                            for (JsonNode meldNode : entry.getValue()) {
                                List<Integer> tiles = new ArrayList<>();
                                meldNode.path("tiles").forEach(tile -> tiles.add(tile.asInt()));
                                seatMelds.add(
                                        new QaRoundTable.Meld(
                                                meldNode.path("combType").asText(),
                                                tiles,
                                                meldNode.path("fromSeat").asInt()));
                            }
                        });
        JsonNode lastDiscard = node.path("lastDiscard");
        if (lastDiscard.isObject()) {
            table.lastDiscard =
                    new QaRoundTable.LastDiscard(
                            lastDiscard.path("seat").asInt(),
                            lastDiscard.path("tile").asInt(),
                            lastDiscard.path("tileIndex").asInt());
        }
        JsonNode pendingKong = node.path("pendingKong");
        if (pendingKong.isObject()) {
            JsonNode pong = pendingKong.path("pong");
            List<Integer> tiles = new ArrayList<>();
            pong.path("tiles").forEach(tile -> tiles.add(tile.asInt()));
            table.pendingKong =
                    new QaRoundTable.PendingKong(
                            pendingKong.path("seat").asInt(),
                            pendingKong.path("tile").asInt(),
                            new QaRoundTable.Meld(
                                    pong.path("combType").asText(),
                                    tiles,
                                    pong.path("fromSeat").asInt()));
        }
        node.path("choices")
                .properties()
                .forEach(
                        entry ->
                                table.choices()
                                        .put(Integer.parseInt(entry.getKey()), entry.getValue().asText()));
        table.nextOfferId = node.path("nextOfferId").asInt();
        QaRoundNodePayloads.readTingInfos(node.path("tingInfos"), table.tingInfos());
        JsonNode offers = node.path("offers");
        offers.properties()
                .forEach(
                        entry ->
                                table.offers()
                                        .put(
                                                Integer.parseInt(entry.getKey()),
                                                QaRoundNodePayloads.readOffer(entry.getValue())));
        JsonNode outcome = node.path("outcome");
        if (outcome.isObject()) {
            Map<Integer, Long> deltas = new LinkedHashMap<>();
            outcome.path("deltas")
                    .properties()
                    .forEach(
                            entry ->
                                    deltas.put(
                                            Integer.parseInt(entry.getKey()),
                                            entry.getValue().asLong()));
            Map<Integer, String> endStates = new LinkedHashMap<>();
            outcome.path("endStates")
                    .properties()
                    .forEach(
                            entry ->
                                    endStates.put(
                                            Integer.parseInt(entry.getKey()),
                                            entry.getValue().asText()));
            table.outcome =
                    new QaRoundTable.Outcome(
                            outcome.path("winnerSeat").asInt(),
                            outcome.path("winType").asText(),
                            outcome.path("discarderSeat").isNumber()
                                    ? outcome.path("discarderSeat").asInt()
                                    : null,
                            outcome.path("winningTile").isNumber()
                                    ? outcome.path("winningTile").asInt()
                                    : null,
                            deltas,
                            endStates);
        }
        table.totalResult = readTotalResult(node.path("totalResult"), table.chairCount);
        return table;
    }

    private Map<String, Object> tableNode(QaRoundTable table) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("version", 12);
        node.put("chairCount", table.chairCount);
        node.put("dealerSeat", table.dealerSeat);
        node.put("roundNumber", table.roundNumber);
        node.put("maxPlayCount", table.maxPlayCount);
        node.put("stage", table.stage.name());
        node.put("turnIndex", table.turnIndex);
        node.put("activeSeat", table.activeSeat);
        node.put("baseScore", table.baseScore);
        node.put("goldMode", table.goldMode);
        node.put("openingCoinsBySeat", new LinkedHashMap<>(table.openingCoinsBySeat));
        node.put("shengPaiCount", table.shengPaiCount);
        node.put("leftBankerCount", table.leftBankerCount);
        node.put("discardedTileTypes", List.copyOf(table.discardedTileTypes));
        node.put(
                "lastDiscardSnapshot",
                table.lastDiscardSnapshot == null
                        ? null
                        : Map.of(
                                "seat", table.lastDiscardSnapshot.seat(),
                                "tile", table.lastDiscardSnapshot.tile(),
                                "shengPaiStage", table.lastDiscardSnapshot.shengPaiStage(),
                                "rawTile", table.lastDiscardSnapshot.rawTile(),
                                "allHandRaw", table.lastDiscardSnapshot.allHandRaw()));
        node.put("baoPaiSeat", table.baoPaiSeat);
        node.put(
                "diceRoll",
                table.diceRoll == null ? null : QaTaizhouProjection.diceRollPayload(table.diceRoll));
        node.put("wall", List.copyOf(table.wall));
        node.put("shuffleAlgorithm", table.shuffleAlgorithm);
        node.put("shuffleSeedSource", table.shuffleSeedSource);
        node.put("shuffleCommitment", table.shuffleCommitment);
        node.put("physicalWallOpening", table.physicalWallOpening);
        node.put("wallOpenIndex", table.wallOpenIndex);
        node.put("wallAsc", table.wallAsc);
        node.put("wallDesc", table.wallDesc);
        node.put("wallFirstAsc", table.wallFirstAsc);
        node.put("wallFirstDesc", table.wallFirstDesc);
        node.put("openTiles", List.copyOf(table.openTiles()));
        node.put("jokerTile", table.jokerRule.jokerTile());
        node.put("insteadTile", table.jokerRule.insteadTile());
        node.put("drawnTileSeat", table.drawnTileSeat);
        node.put("drawnTile", table.drawnTile);
        node.put("hands", tileMapNode(table.hands()));
        node.put("rivers", tileMapNode(table.rivers()));
        node.put("flowers", tileMapNode(table.flowers()));
        Map<String, Object> melds = new LinkedHashMap<>();
        table.melds()
                .forEach(
                        (seat, seatMelds) -> {
                            List<Map<String, Object>> payload = new ArrayList<>();
                            for (QaRoundTable.Meld meld : seatMelds) {
                                payload.add(QaTaizhouProjection.meldPayload(meld));
                            }
                            melds.put(Integer.toString(seat), payload);
                        });
        node.put("melds", melds);
        node.put(
                "lastDiscard",
                table.lastDiscard == null
                        ? null
                        : Map.of(
                                "seat", table.lastDiscard.seat(),
                                "tile", table.lastDiscard.tile(),
                                "tileIndex", table.lastDiscard.tileIndex()));
        node.put(
                "pendingKong",
                table.pendingKong == null
                        ? null
                        : Map.of(
                                "seat", table.pendingKong.seat(),
                                "tile", table.pendingKong.tile(),
                                "pong", QaTaizhouProjection.meldPayload(table.pendingKong.pong())));
        Map<String, Object> offers = new LinkedHashMap<>();
        table.offers()
                .forEach(
                        (seat, offer) ->
                                offers.put(
                                        Integer.toString(seat),
                                        QaRoundNodePayloads.offerNode(offer)));
        node.put("offers", offers);
        node.put("botSeats", List.copyOf(table.botSeats));
        node.put("choices", new LinkedHashMap<>(table.choices()));
        node.put("nextOfferId", table.nextOfferId);
        node.put("tingInfos", QaRoundNodePayloads.tingInfosNode(table.tingInfos()));
        node.put("passedHuTiles", setMapNode(table.passedHuTiles()));
        node.put("passedPungTiles", setMapNode(table.passedPungTiles()));
        if (table.outcome != null) {
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("winnerSeat", table.outcome.winnerSeat());
            outcome.put("winType", table.outcome.winType());
            outcome.put("discarderSeat", table.outcome.discarderSeat());
            outcome.put("winningTile", table.outcome.winningTile());
            outcome.put("deltas", table.outcome.deltas());
            outcome.put("endStates", table.outcome.endStates());
            node.put("outcome", outcome);
        } else {
            node.put("outcome", null);
        }
        node.put("totalResult", table.totalResult);
        return node;
    }

    private static Object wallStateNode(QaRoundTable table) {
        if (table.wallFirstAsc < 0) {
            return null;
        }
        return Map.of(
                "nWallCnt", table.wall.size(),
                "nAsc", table.wallAsc,
                "nDesc", table.wallDesc,
                "nFirstAsc", table.wallFirstAsc,
                "nFirstDesc", table.wallFirstDesc,
                "bShow", 1,
                "nOpenIndex", table.wallOpenIndex);
    }

    private static QaTaizhouTotalResult readTotalResult(JsonNode node, int chairCount) {
        if (!node.isObject()) {
            return QaTaizhouTotalResult.empty(chairCount);
        }
        Map<Integer, QaTaizhouTotalResult.SeatTotal> seats = new LinkedHashMap<>();
        JsonNode seatNodes = node.path("seats");
        for (int seat = 1; seat <= chairCount; seat++) {
            JsonNode seatNode = seatNodes.path(Integer.toString(seat));
            List<Long> roundWinLost = new ArrayList<>();
            seatNode.path("roundWinLost").forEach(value -> roundWinLost.add(value.asLong()));
            List<String> maxFanNames = new ArrayList<>();
            seatNode.path("maxFanNames").forEach(value -> maxFanNames.add(value.asText()));
            seats.put(
                    seat,
                    new QaTaizhouTotalResult.SeatTotal(
                            roundWinLost,
                            seatNode.path("maxHuCount").asInt(),
                            seatNode.path("maxFanNum").asInt(),
                            seatNode.path("maxFanCount").asInt(),
                            maxFanNames,
                            seatNode.path("winByOwn").asInt(),
                            seatNode.path("winScoreNum").asInt(),
                            seatNode.path("jiePaoNum").asInt(),
                            seatNode.path("discardNum").asInt(),
                            seatNode.path("maxScore").asLong(),
                            seatNode.path("laZiNum").asInt(),
                            seatNode.path("chengBaoNum").asInt()));
        }
        return new QaTaizhouTotalResult(node.path("playCount").asInt(), seats);
    }

    private static Map<String, Object> tileMapNode(Map<Integer, List<Integer>> map) {
        Map<String, Object> node = new LinkedHashMap<>();
        map.forEach(
                (seat, tiles) -> node.put(Integer.toString(seat), List.copyOf(tiles)));
        return node;
    }

    private static Map<String, Object> setMapNode(Map<Integer, Set<Integer>> map) {
        Map<String, Object> node = new LinkedHashMap<>();
        map.forEach((seat, tiles) -> node.put(Integer.toString(seat), List.copyOf(tiles)));
        return node;
    }

    private static void readSetMap(JsonNode node, Map<Integer, Set<Integer>> target) {
        node.properties()
                .forEach(
                        entry -> {
                            Set<Integer> tiles = target.get(Integer.parseInt(entry.getKey()));
                            if (tiles != null) {
                                entry.getValue().forEach(tile -> tiles.add(tile.asInt()));
                            }
                        });
    }

    private static void readTileMap(JsonNode node, Map<Integer, List<Integer>> target) {
        node.properties()
                .forEach(
                        entry -> {
                            List<Integer> tiles =
                                    target.get(Integer.parseInt(entry.getKey()));
                            tiles.clear();
                            entry.getValue().forEach(tile -> tiles.add(tile.asInt()));
                        });
    }

    private static QaRoundTable.DiceRoll readDiceRoll(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        List<Integer> values = new ArrayList<>();
        node.path("nChips").forEach(value -> values.add(value.asInt()));
        return new QaRoundTable.DiceRoll(
                node.path("nSeat").asInt(),
                values,
                node.path("gameStep").asInt(),
                node.path("showAni").asBoolean(false));
    }
}
