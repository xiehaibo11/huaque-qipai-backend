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

final class QaMahjongRoundProjection {
    private static final int WINNER_SEAT = 1;
    private static final DateTimeFormatter SETTLE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;

    QaMahjongRoundProjection(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    JsonNode visibleRoundsBySeat(
            QaMahjongAutoRoundEngine.Request request,
            Map<Integer, List<Integer>> hands,
            Map<Integer, List<Integer>> rivers,
            Integer lastDiscardSeat) {
        Map<String, JsonNode> bySeat = new LinkedHashMap<>();
        for (QaMahjongAutoRoundEngine.SeatInput viewer : request.seats()) {
            bySeat.put(
                    Integer.toString(viewer.seatNumber()),
                    visibleRound(request, viewer.seatNumber(), hands, rivers, lastDiscardSeat));
        }
        return node(bySeat);
    }

    JsonNode visibleRound(
            QaMahjongAutoRoundEngine.Request request,
            int viewerSeat,
            Map<Integer, List<Integer>> hands,
            Map<Integer, List<Integer>> rivers,
            Integer lastDiscardSeat) {
        List<Map<String, Object>> handPayload = new ArrayList<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : request.seats()) {
            List<Integer> seatHand = hands.get(seat.seatNumber());
            handPayload.add(
                    Map.of(
                            "seatNumber",
                            seat.seatNumber(),
                            "concealedTiles",
                            seat.seatNumber() == viewerSeat
                                    ? List.copyOf(seatHand)
                                    : backs(seatHand.size()),
                            "meldCount",
                            0));
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chairCount", request.chairCount());
        value.put("mySeat", viewerSeat);
        value.put("hands", handPayload);
        value.put("jokerTiles", List.of());
        value.put("insteadTiles", List.of());
        value.put("rivers", riverPayload(request.chairCount(), rivers));
        if (lastDiscardSeat != null) {
            List<Integer> river = rivers.get(lastDiscardSeat);
            value.put(
                    "lastDiscard",
                    Map.of("seatNumber", lastDiscardSeat, "tileIndex", river.size() - 1));
        }
        return node(value);
    }

    JsonNode publicRound(
            QaMahjongAutoRoundEngine.Request request,
            Map<Integer, List<Integer>> rivers,
            Integer lastDiscardSeat) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chairCount", request.chairCount());
        value.put("rivers", riverPayload(request.chairCount(), rivers));
        if (lastDiscardSeat != null) {
            List<Integer> river = rivers.get(lastDiscardSeat);
            value.put(
                    "lastDiscard",
                    Map.of("seatNumber", lastDiscardSeat, "tileIndex", river.size() - 1));
        }
        return node(value);
    }

    JsonNode multipleChoice(QaMahjongAutoRoundEngine.Request request) {
        List<Map<String, Object>> seatChoices = new ArrayList<>();
        String[] choices = {null, "NONE", "ADD", "SUPER"};
        for (QaMahjongAutoRoundEngine.SeatInput seat : request.seats()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seatNumber", seat.seatNumber());
            entry.put("choice", choices[Math.min(seat.seatNumber() - 1, choices.length - 1)]);
            seatChoices.add(entry);
        }
        return node(
                Map.of(
                        "goldMode", true,
                        "choiceActive", true,
                        "baseScore", 60,
                        "currentMultiplier", 1,
                        "cardUseCount", 1,
                        "diamondUseCount", 50,
                        "mySeat", 1,
                        "allowedChoices", List.of("NONE", "ADD", "SUPER"),
                        "seatChoices", seatChoices));
    }

    JsonNode settlement(
            QaMahjongAutoRoundEngine.Request request,
            int roundNumber,
            Map<Integer, List<Integer>> hands,
            Map<Integer, Long> deltas) {
        List<Map<String, Object>> seats = new ArrayList<>();
        for (QaMahjongAutoRoundEngine.SeatInput seat : request.seats()) {
            long delta = deltas.get(seat.seatNumber());
            seats.add(
                    Map.ofEntries(
                            Map.entry("seatNumber", seat.seatNumber()),
                            Map.entry("displayName", seat.displayName()),
                            Map.entry("publicPlayerId", Long.toString(seat.publicPlayerId())),
                            Map.entry("wind", seat.seatNumber()),
                            Map.entry("banker", seat.seatNumber() == WINNER_SEAT),
                            Map.entry("handHu", seat.seatNumber() == WINNER_SEAT ? 18 : 0),
                            Map.entry("tai", 0),
                            Map.entry("totalHu", seat.seatNumber() == WINNER_SEAT ? 18 : 0),
                            Map.entry("playerState", seat.seatNumber() == WINNER_SEAT ? 1 : 0),
                            Map.entry("fan", seat.seatNumber() == WINNER_SEAT ? 3 : 0),
                            Map.entry("gangScore", 0),
                            Map.entry("total", delta),
                            Map.entry("delta", delta),
                            Map.entry("hasCaishen", false),
                            Map.entry("handTiles", List.copyOf(hands.get(seat.seatNumber())))));
        }
        return node(
                Map.of(
                        "result",
                        "ZIMO",
                        "roomNumber",
                        request.roomNumber(),
                        "roundLabel",
                        "第" + roundNumber + "局",
                        "time",
                        SETTLE_TIME.format(request.occurredAt()),
                        "gameRule",
                        request.gameRuleDisplay(),
                        "seats",
                        seats));
    }

    JsonNode node(Object value) {
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private static List<Integer> backs(int count) {
        List<Integer> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(0x72);
        }
        return result;
    }

    private static List<Map<String, Object>> riverPayload(
            int chairCount, Map<Integer, List<Integer>> rivers) {
        int maxLineCount = chairCount == 2 ? 2 : 3;
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rivers.entrySet()) {
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
