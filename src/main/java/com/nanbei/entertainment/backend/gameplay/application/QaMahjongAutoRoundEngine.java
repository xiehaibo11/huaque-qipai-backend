package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** QA-only deterministic Taizhou table driver for end-to-end event-chain testing. */
final class QaMahjongAutoRoundEngine {
    static final int BOT_POOL_SIZE = 300;
    private static final int WINNER_SEAT = 1;
    private static final int AUTO_TURN_COUNT = 12;
    private final ObjectMapper objectMapper;
    private final QaMahjongRoundProjection projection;

    QaMahjongAutoRoundEngine(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.projection = new QaMahjongRoundProjection(objectMapper);
    }

    QaMahjongAutoRoundResult play(Request request) {
        request.validate();
        long nextRevision = request.expectedRevision() + 1L;
        int roundNumber = request.currentRoundNumber() + 1;
        List<Integer> wall = shuffledWall(request);
        Map<Integer, List<Integer>> hands = new LinkedHashMap<>();
        Map<Integer, List<Integer>> rivers = new LinkedHashMap<>();
        for (SeatInput seat : request.seats()) {
            hands.put(seat.seatNumber(), new ArrayList<>());
            rivers.put(seat.seatNumber(), new ArrayList<>());
        }
        for (int deal = 0; deal < 13; deal++) {
            for (SeatInput seat : request.seats()) {
                hands.get(seat.seatNumber()).add(draw(wall));
            }
        }

        List<GameEvent> events = new ArrayList<>();
        events.add(publicEvent(nextRevision, "BOT_SEATS_FILLED", botSeatsPayload(request)));
        events.add(
                publicEvent(
                        nextRevision,
                        "WALL_SHUFFLED",
                        Map.of(
                                "qaMode",
                                true,
                                "algorithm",
                                "NANBEI_QA_DETERMINISTIC_V1",
                                "wallSize",
                                wall.size() + request.seats().size() * 13,
                                "remainingWallCount",
                                wall.size())));
        events.add(publicEvent(nextRevision, "MULTIPLE_CHOICE_STARTED", Map.of(
                "qaMode", true, "phase", GamePhase.DEALING.name(),
                "roundNumber", roundNumber, "remainingWallCount", wall.size(),
                "multipleChoice", projection.multipleChoice(request))));
        Map<String, Object> dealtPublicPayload =
                publicRoundPayload(
                        request,
                        roundNumber,
                        GamePhase.DEALING,
                        0,
                        null,
                        rivers,
                        null,
                        wall.size());
        dealtPublicPayload.put("multipleChoice", objectMapper.nullNode());
        events.add(publicEvent(nextRevision, "DEALT", dealtPublicPayload));
        for (SeatInput seat : request.seats()) {
            events.add(
                    seatEvent(
                            nextRevision,
                            "DEALT",
                            seat.seatNumber(),
                            roundPayload(
                                    request,
                                    roundNumber,
                                    GamePhase.DEALING,
                                    0,
                                    null,
                                    seat.seatNumber(),
                                    hands,
                                    rivers,
                                    null,
                                    wall.size())));
        }

        Integer lastDiscardTile = null;
        for (int turn = 1; turn <= AUTO_TURN_COUNT; turn++) {
            SeatInput seat = request.seats().get((turn - 1) % request.seats().size());
            int seatNumber = seat.seatNumber();
            int drawnTile = draw(wall);
            hands.get(seatNumber).add(drawnTile);
            events.add(
                    publicEvent(
                            nextRevision,
                            "DRAWN",
                            publicRoundPayload(
                                    request,
                                    roundNumber,
                                    GamePhase.PLAYING,
                                    turn,
                                    seatNumber,
                                    rivers,
                                    null,
                                    wall.size())));
            events.add(
                    seatEvent(
                            nextRevision,
                            "DRAWN",
                            seatNumber,
                            roundPayload(
                                    request,
                                    roundNumber,
                                    GamePhase.PLAYING,
                                    turn,
                                    seatNumber,
                                    seatNumber,
                                    hands,
                                    rivers,
                                    null,
                                    wall.size())));
            int discardedTile = discardChoice(hands.get(seatNumber), turn == AUTO_TURN_COUNT);
            hands.get(seatNumber).remove(Integer.valueOf(discardedTile));
            List<Integer> river = rivers.get(seatNumber);
            river.add(discardedTile);
            lastDiscardTile = discardedTile;
            events.add(
                    publicEvent(
                            nextRevision,
                            "DISCARDED",
                            publicRoundPayload(
                                    request,
                                    roundNumber,
                                    GamePhase.PLAYING,
                                    turn,
                                    seatNumber,
                                    rivers,
                                    Map.of("seatNumber", seatNumber, "tile", discardedTile, "tileIndex", river.size() - 1),
                                    wall.size())));
            events.add(
                    seatEvent(
                            nextRevision,
                            "DISCARDED",
                            seatNumber,
                            roundPayload(
                                    request,
                                    roundNumber,
                                    GamePhase.PLAYING,
                                    turn,
                                    seatNumber,
                                    seatNumber,
                                    hands,
                                    rivers,
                                    Map.of("seatNumber", seatNumber, "tile", discardedTile, "tileIndex", river.size() - 1),
                                    wall.size())));
        }

        Map<Integer, Long> deltas = scoreDeltas(request.seats());
        JsonNode settlement = projection.settlement(request, roundNumber, hands, deltas);
        JsonNode visibleRoundsBySeat = projection.visibleRoundsBySeat(request, hands, rivers, lastDiscardSeat(rivers));
        JsonNode state =
                projection.node(
                        Map.of(
                                "qaMode",
                                true,
                                "qaDisclosure",
                                "QA auto round only; production START_ROUND uses Nanbei SERVER_AUTHORITY and is not original server code.",
                                "roundNumber",
                                roundNumber,
                                "remainingWallCount",
                                wall.size(),
                                "visibleRoundsBySeat",
                                visibleRoundsBySeat,
                                "playPermissionsBySeat",
                                Map.of(),
                                "settlement",
                                settlement));
        events.add(
                publicEvent(
                        nextRevision,
                        "WIN_DECLARED",
                        Map.of(
                                "qaMode",
                                true,
                                "phase",
                                GamePhase.PLAYING.name(),
                                "roundNumber",
                                roundNumber,
                                "winnerSeat",
                                WINNER_SEAT,
                                "winType",
                                "ZIMO",
                                "trigger",
                                "QA_AUTO_TURN_" + AUTO_TURN_COUNT)));
        events.add(
                publicEvent(
                        nextRevision,
                        "SCORES_SETTLED",
                        Map.of(
                                "qaMode",
                                true,
                                "roundNumber",
                                roundNumber,
                                "scores",
                                scorePayload(request.seats(), deltas))));
        events.add(
                publicEvent(
                        nextRevision,
                        "ROUND_RESULT_READY",
                        Map.of(
                                "qaMode",
                                true,
                                "phase",
                                GamePhase.ROUND_RESULT.name(),
                                "roundNumber",
                                roundNumber,
                                "settlement",
                                settlement)));
        return new QaMahjongAutoRoundResult(
                GamePhase.ROUND_RESULT,
                roundNumber,
                nextRevision,
                state,
                events,
                deltas);
    }

    private List<Integer> shuffledWall(Request request) {
        List<Integer> wall = new ArrayList<>(136);
        for (int tile : baseTiles()) {
            for (int copy = 0; copy < 4; copy++) {
                wall.add(tile);
            }
        }
        for (int tile = 0x61; tile <= 0x68; tile++) {
            wall.add(tile);
        }
        Collections.shuffle(wall, new Random(seed(request)));
        return wall;
    }

    private static List<Integer> baseTiles() {
        List<Integer> tiles = new ArrayList<>();
        for (int suit : List.of(0x10, 0x20, 0x30)) {
            for (int rank = 1; rank <= 9; rank++) {
                tiles.add(suit + rank);
            }
        }
        for (int rank = 1; rank <= 4; rank++) {
            tiles.add(0x40 + rank);
        }
        for (int rank = 1; rank <= 3; rank++) {
            tiles.add(0x50 + rank);
        }
        return tiles;
    }

    private static long seed(Request request) {
        return Objects.hash(
                request.roomNumber(),
                request.expectedRevision(),
                request.currentRoundNumber(),
                request.seats().stream().map(SeatInput::userId).toList());
    }

    private static int draw(List<Integer> wall) {
        if (wall.isEmpty()) {
            throw new IllegalStateException("QA wall exhausted before scripted settlement");
        }
        return wall.remove(0);
    }

    private static int discardChoice(List<Integer> hand, boolean finalTurn) {
        if (hand.isEmpty()) {
            throw new IllegalStateException("cannot discard from empty hand");
        }
        if (finalTurn) {
            return hand.get(hand.size() - 1);
        }
        return hand.stream().max(Comparator.naturalOrder()).orElse(hand.get(hand.size() - 1));
    }

    private static Map<Integer, Long> scoreDeltas(List<SeatInput> seats) {
        Map<Integer, Long> deltas = new LinkedHashMap<>();
        int losers = seats.size() - 1;
        for (SeatInput seat : seats) {
            deltas.put(
                    seat.seatNumber(),
                    seat.seatNumber() == WINNER_SEAT ? (long) losers * 100L : -100L);
        }
        return deltas;
    }

    private static List<Map<String, Object>> scorePayload(
            List<SeatInput> seats, Map<Integer, Long> deltas) {
        List<Map<String, Object>> scores = new ArrayList<>();
        for (SeatInput seat : seats) {
            scores.add(
                    Map.of(
                            "seatNumber",
                            seat.seatNumber(),
                            "score",
                            seat.score() + deltas.get(seat.seatNumber()),
                            "delta",
                            deltas.get(seat.seatNumber())));
        }
        return scores;
    }

    private static Map<String, Object> botSeatsPayload(Request request) {
        List<Map<String, Object>> seats = new ArrayList<>();
        for (SeatInput seat : request.seats()) {
            seats.add(
                    Map.of(
                            "seatNumber",
                            seat.seatNumber(),
                            "userId",
                            seat.userId().toString(),
                            "displayName",
                            seat.displayName(),
                            "qaBot",
                            seat.qaBot()));
        }
        return Map.of("qaMode", true, "botPoolSize", BOT_POOL_SIZE,
                "chairCount", request.chairCount(), "seats", seats);
    }

    private Map<String, Object> publicRoundPayload(
            Request request,
            int roundNumber,
            GamePhase phase,
            int turnIndex,
            Integer activeSeat,
            Map<Integer, List<Integer>> rivers,
            Map<String, Object> lastDiscard,
            int remainingWallCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qaMode", true);
        payload.put("phase", phase.name());
        payload.put("roundNumber", roundNumber);
        payload.put("turnIndex", turnIndex);
        if (activeSeat != null) {
            payload.put("activeSeat", activeSeat);
        }
        payload.put("remainingWallCount", remainingWallCount);
        payload.put(
                "publicRound",
                projection.publicRound(
                        request,
                        rivers,
                        lastDiscard == null
                                ? null
                                : (Integer) lastDiscard.get("seatNumber")));
        if (lastDiscard != null) {
            payload.put("lastDiscard", lastDiscard);
        }
        return payload;
    }

    private Map<String, Object> roundPayload(
            Request request,
            int roundNumber,
            GamePhase phase,
            int turnIndex,
            Integer activeSeat,
            int viewerSeat,
            Map<Integer, List<Integer>> hands,
            Map<Integer, List<Integer>> rivers,
            Map<String, Object> lastDiscard,
            int remainingWallCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qaMode", true);
        payload.put("phase", phase.name());
        payload.put("roundNumber", roundNumber);
        payload.put("turnIndex", turnIndex);
        if (activeSeat != null) {
            payload.put("activeSeat", activeSeat);
        }
        payload.put("remainingWallCount", remainingWallCount);
        payload.put(
                "visibleRound",
                projection.visibleRound(
                        request,
                        viewerSeat,
                        hands,
                        rivers,
                        lastDiscard == null
                                ? null
                                : (Integer) lastDiscard.get("seatNumber")));
        if (lastDiscard != null) {
            payload.put("lastDiscard", lastDiscard);
        }
        return payload;
    }

    private GameEvent publicEvent(long revision, String type, Map<String, Object> payload) {
        return GameEvent.publicEvent(revision, type, payload);
    }

    private GameEvent seatEvent(
            long revision, String type, int targetSeat, Map<String, Object> payload) {
        return GameEvent.seatEvent(revision, type, targetSeat, payload);
    }

    private static Integer lastDiscardSeat(Map<Integer, List<Integer>> rivers) {
        Integer result = null;
        int max = -1;
        for (Map.Entry<Integer, List<Integer>> entry : rivers.entrySet()) {
            if (entry.getValue().size() > max) {
                max = entry.getValue().size();
                result = entry.getKey();
            }
        }
        return max <= 0 ? null : result;
    }

    record Request(
            long gameId,
            String roomNumber,
            int chairCount,
            int maxPlayCount,
            String gameRuleDisplay,
            long expectedRevision,
            int currentRoundNumber,
            List<SeatInput> seats,
            Instant occurredAt) {
        Request {
            seats = List.copyOf(seats);
        }

        void validate() {
            if (gameId <= 0 || expectedRevision < 0 || currentRoundNumber < 0) {
                throw new IllegalArgumentException("invalid QA round cursor");
            }
            if (roomNumber == null || !roomNumber.matches("\\d{6}")) {
                throw new IllegalArgumentException("invalid roomNumber");
            }
            if (chairCount != seats.size() || (chairCount != 2 && chairCount != 4)) {
                throw new IllegalArgumentException("QA round requires a full two- or four-seat table");
            }
            if (maxPlayCount <= 0 || gameRuleDisplay == null || gameRuleDisplay.isBlank()) {
                throw new IllegalArgumentException("invalid QA table metadata");
            }
            Set<Integer> seenSeats = new HashSet<>();
            for (SeatInput seat : seats) {
                if (!seenSeats.add(seat.seatNumber()) || seat.seatNumber() < 1 || seat.seatNumber() > chairCount) {
                    throw new IllegalArgumentException("invalid QA seat order");
                }
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record SeatInput(
            int seatNumber,
            UUID userId,
            String displayName,
            long publicPlayerId,
            long score,
            boolean qaBot) {
        SeatInput {
            Objects.requireNonNull(userId, "userId");
            if (displayName == null || displayName.isBlank() || publicPlayerId <= 0 || score < 0) {
                throw new IllegalArgumentException("invalid QA seat profile");
            }
        }
    }
}
