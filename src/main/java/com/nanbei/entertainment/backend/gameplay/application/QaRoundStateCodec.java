package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        state.put("shengPaiCount", table.shengPaiCount < 0 ? null : table.shengPaiCount);
        state.put("leftBankerCount", table.leftBankerCount < 0 ? null : table.leftBankerCount);
        state.put("tingInfosBySeat", projection.tingInfosBySeat(table));
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
                        botSeats);
        table.stage = QaRoundTable.Stage.valueOf(node.path("stage").asText());
        table.turnIndex = node.path("turnIndex").asInt();
        table.activeSeat = node.path("activeSeat").asInt();
        table.shengPaiCount = node.path("shengPaiCount").asInt(-1);
        table.leftBankerCount = node.path("leftBankerCount").asInt(-1);
        table.diceRoll = readDiceRoll(node.path("diceRoll"));
        node.path("wall").forEach(tile -> table.wall.add(tile.asInt()));
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
                            deltas,
                            endStates);
        }
        return table;
    }

    private Map<String, Object> tableNode(QaRoundTable table) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("version", 5);
        node.put("chairCount", table.chairCount);
        node.put("dealerSeat", table.dealerSeat);
        node.put("roundNumber", table.roundNumber);
        node.put("stage", table.stage.name());
        node.put("turnIndex", table.turnIndex);
        node.put("activeSeat", table.activeSeat);
        node.put("shengPaiCount", table.shengPaiCount);
        node.put("leftBankerCount", table.leftBankerCount);
        node.put(
                "diceRoll",
                table.diceRoll == null ? null : QaTaizhouProjection.diceRollPayload(table.diceRoll));
        node.put("wall", List.copyOf(table.wall));
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
        if (table.outcome != null) {
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("winnerSeat", table.outcome.winnerSeat());
            outcome.put("winType", table.outcome.winType());
            outcome.put("discarderSeat", table.outcome.discarderSeat());
            outcome.put("deltas", table.outcome.deltas());
            outcome.put("endStates", table.outcome.endStates());
            node.put("outcome", outcome);
        } else {
            node.put("outcome", null);
        }
        return node;
    }

    private static Map<String, Object> tileMapNode(Map<Integer, List<Integer>> map) {
        Map<String, Object> node = new LinkedHashMap<>();
        map.forEach(
                (seat, tiles) -> node.put(Integer.toString(seat), List.copyOf(tiles)));
        return node;
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
