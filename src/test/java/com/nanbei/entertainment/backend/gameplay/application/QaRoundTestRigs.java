package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** QA 台州引擎测试共用的确定性牌墙与上下文装配。 */
final class QaRoundTestRigs {
    static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    private QaRoundTestRigs() {}

    static QaRoundContext humanDealerContext() {
        return new QaRoundContext(
                "123456", "不平搓/不封顶", QaTaizhouRoundEngineTest.seats(false, true, true, true), NOW);
    }

    static QaRoundContext botDealerContext() {
        return new QaRoundContext(
                "123456", "不平搓/不封顶", QaTaizhouRoundEngineTest.seats(true, false, true, true), NOW);
    }

    static QaRoundContext allBotContext() {
        return new QaRoundContext(
                "123456", "不平搓/不封顶", QaTaizhouRoundEngineTest.seats(true, true, true, true), NOW);
    }

    static QaTaizhouRoundEngine.Request humanDealerRequest() {
        return QaTaizhouRoundEngineTest.request(
                QaTaizhouRoundEngineTest.seats(false, true, true, true));
    }

    static QaTaizhouRoundEngine.Request botDealerRequest() {
        return QaTaizhouRoundEngineTest.request(
                QaTaizhouRoundEngineTest.seats(true, false, true, true));
    }

    static QaRoundStep humanDealerAfterMultipleChoice(QaTaizhouRoundEngine engine)
            throws Exception {
        QaTaizhouRoundResult started = engine.start(humanDealerRequest());
        return chooseMultiple(engine, started, humanDealerContext(), 1, "PASS", 2L);
    }

    static QaRoundStep humanDealerAfterMultipleChoice(
            QaTaizhouRoundEngine engine, List<Integer> wall) throws Exception {
        QaTaizhouRoundResult started = engine.start(humanDealerRequest(), wall);
        return chooseMultiple(engine, started, humanDealerContext(), 1, "PASS", 2L);
    }

    static QaRoundStep botDealerAfterMultipleChoice(QaTaizhouRoundEngine engine, List<Integer> wall)
            throws Exception {
        QaTaizhouRoundResult started = engine.start(botDealerRequest(), wall);
        return chooseMultiple(engine, started, botDealerContext(), 2, "PASS", 2L);
    }

    static QaRoundStep chooseMultiple(
            QaTaizhouRoundEngine engine,
            QaTaizhouRoundResult started,
            QaRoundContext context,
            int actorSeat,
            String choice,
            long revision)
            throws Exception {
        return engine.apply(
                started.table(),
                context,
                actorSeat,
                GameplayCommandType.MULTIPLE_CHOICE,
                new tools.jackson.databind.ObjectMapper()
                        .readTree("{\"choice\":\"" + choice + "\"}"),
                revision);
    }

    static Map<String, Object> lastOffer(List<GameEvent> events, int seat) {
        for (int index = events.size() - 1; index >= 0; index--) {
            GameEvent event = events.get(index);
            if (event.type().equals("ACTION_OFFERED") && event.targetSeat() == seat) {
                return event.payload();
            }
        }
        throw new AssertionError("no ACTION_OFFERED for seat " + seat);
    }

    static String lastOfferToken(List<GameEvent> events, int seat) {
        return (String) lastOffer(events, seat).get("actionToken");
    }

    static List<Integer> chowWall() {
        return riggedWall(
                List.of(0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37),
                List.of(0x37, 0x38, 0x41, 0x42, 0x43, 0x44, 0x51, 0x52, 0x53, 0x21, 0x25, 0x26, 0x27),
                List.of(0x12, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x28, 0x29, 0x51, 0x52, 0x53),
                List.of(0x22, 0x24, 0x26, 0x27, 0x32, 0x34, 0x36, 0x38, 0x41, 0x42, 0x43, 0x44, 0x51),
                List.of(0x39));
    }

    static List<Integer> pungWall() {
        return riggedWall(
                List.of(0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x21, 0x22, 0x23, 0x24),
                List.of(0x25, 0x25, 0x41, 0x42, 0x43, 0x44, 0x51, 0x52, 0x53, 0x31, 0x33, 0x35, 0x37),
                List.of(0x12, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x28, 0x29, 0x51, 0x52, 0x53),
                List.of(0x22, 0x24, 0x26, 0x27, 0x32, 0x34, 0x36, 0x38, 0x41, 0x42, 0x43, 0x44, 0x51),
                List.of(0x25));
    }

    static List<Integer> exposedKongWall() {
        List<Integer> wall =
                riggedWall(
                        List.of(0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x21, 0x22, 0x23, 0x24),
                        List.of(0x25, 0x25, 0x25, 0x41, 0x42, 0x43, 0x44, 0x51, 0x52, 0x53, 0x31, 0x33, 0x35),
                        List.of(0x12, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x28, 0x29, 0x51, 0x52, 0x53),
                        List.of(0x22, 0x24, 0x26, 0x27, 0x32, 0x34, 0x36, 0x38, 0x41, 0x42, 0x43, 0x44, 0x51),
                        List.of(0x25));
        wall.set(54, 0x19);
        return wall;
    }

    static List<Integer> concealedKongWall() {
        List<Integer> wall =
                riggedWall(
                        List.of(0x37, 0x37, 0x37, 0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41),
                        List.of(0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x51),
                        List.of(0x12, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x28, 0x29, 0x51, 0x52, 0x53),
                        List.of(0x22, 0x24, 0x26, 0x27, 0x32, 0x34, 0x36, 0x38, 0x41, 0x42, 0x43, 0x44, 0x51),
                        List.of(0x37));
        wall.set(54, 0x19);
        return wall;
    }

    static List<Integer> selfHuWall() {
        return riggedWall(
                List.of(0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x41),
                List.of(0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x51),
                List.of(0x12, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x28, 0x29, 0x51, 0x52, 0x53),
                List.of(0x22, 0x24, 0x26, 0x27, 0x32, 0x34, 0x36, 0x38, 0x41, 0x42, 0x43, 0x44, 0x51),
                List.of(0x41));
    }

    static List<Integer> discardHuWall() {
        return riggedWall(
                List.of(0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37),
                List.of(0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x41, 0x37, 0x38),
                List.of(0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x51),
                List.of(0x42, 0x43, 0x44, 0x51, 0x52, 0x53, 0x13, 0x15, 0x17, 0x19, 0x24, 0x26, 0x28),
                List.of(0x39));
    }

    static List<Integer> riggedWall(
            List<Integer> seat1,
            List<Integer> seat2,
            List<Integer> seat3,
            List<Integer> seat4,
            List<Integer> draws) {
        List<List<Integer>> bySeat = List.of(seat1, seat2, seat3, seat4);
        List<Integer> wall = new ArrayList<>(QaTaizhouTiles.WALL_SIZE);
        // 翻得牌先于 52 张基础发牌消费；0x29 不在这些夹具的庄家/目标牌组或首摸牌中。
        wall.add(0x29);
        for (int round = 0; round < 13; round++) {
            for (int seat = 0; seat < 4; seat++) {
                wall.add(bySeat.get(seat).get(round));
            }
        }
        wall.addAll(draws);
        int[] pad = {
            0x11, 0x14, 0x17, 0x21, 0x24, 0x27, 0x31, 0x34, 0x37, 0x41, 0x43, 0x51, 0x53
        };
        int index = 0;
        while (wall.size() < QaTaizhouTiles.WALL_SIZE) {
            wall.add(pad[index % pad.length]);
            index++;
        }
        return wall;
    }

    /** 某座位「打出后能听」的牌值集合，取自该回合下发的 {@code TING_INFO} 事件。 */
    static Set<Integer> tingDiscardsFor(List<GameEvent> events, int seat) {
        Set<Integer> discards = new LinkedHashSet<>();
        for (GameEvent event : events) {
            if (!"TING_INFO".equals(event.type())
                    || !Integer.valueOf(seat).equals(event.targetSeat())) {
                continue;
            }
            for (Object raw : (List<?>) event.payload().get("tingMahs")) {
                Map<?, ?> ting = (Map<?, ?>) raw;
                if (!((List<?>) ting.get("huTargets")).isEmpty()) {
                    discards.add((Integer) ting.get("discard"));
                }
            }
        }
        return discards;
    }

    /** 原始索引 0 是刚摸进的牌，1..n 是暗牌区立牌。 */
    static int tileValueAt(JsonNode hand, int originalIndex) {
        return originalIndex == 0
                ? hand.path("drawnTile").asInt()
                : hand.path("concealedTiles").get(originalIndex - 1).asInt();
    }
}
