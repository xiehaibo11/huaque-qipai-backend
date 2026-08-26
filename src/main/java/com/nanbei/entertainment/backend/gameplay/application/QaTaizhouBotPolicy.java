package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** 测试专场金币场 AI 决策：模型只能从服务端生成的合法动作中选择。 */
final class QaTaizhouBotPolicy {
    enum Action {
        DISCARD,
        CHOW,
        PUNG,
        KONG,
        HU,
        PASS
    }

    record Decision(Action action, int tile, String kongType, int candidateIndex) {}

    @FunctionalInterface
    interface DecisionSource {
        Optional<String> choose(String tableState, List<String> legalActions);
    }

    private static final DecisionSource LOCAL_ONLY = (ignored, legalActions) -> Optional.empty();
    private final DecisionSource decisionSource;

    QaTaizhouBotPolicy() {
        this(LOCAL_ONLY);
    }

    QaTaizhouBotPolicy(DecisionSource decisionSource) {
        this.decisionSource = decisionSource == null ? LOCAL_ONLY : decisionSource;
    }

    Decision decideTurn(QaRoundTable table, int seat) {
        List<String> legal = new ArrayList<>();
        List<Integer> hand = table.hands().get(seat);
        if (QaWinDetector.canWin(hand, table.jokerRule)) {
            legal.add("HU");
        }
        for (QaMeldCandidates.KongOption option :
                QaMeldCandidates.ownDrawKongOptions(
                        hand, drawnTile(table, seat), pongMelds(table, seat), table.jokerRule)) {
            legal.add("KONG:" + option.kongType() + ":" + option.tileValue());
        }
        new LinkedHashSet<>(hand).stream()
                .filter(QaTaizhouTiles::isPlayable)
                .filter(tile -> !table.jokerRule.isJoker(tile))
                .sorted()
                .map(tile -> "DISCARD:" + tile)
                .forEach(legal::add);
        if (legal.isEmpty()) {
            throw new IllegalStateException("AI has no legal turn action");
        }
        return parse(choose(table, tableState(table, seat, null), legal, fallbackTurn(legal)));
    }

    Decision decideDiscardClaim(QaRoundTable table, int seat, int discarder, int tile) {
        List<String> legal = new ArrayList<>();
        List<Integer> hand = table.hands().get(seat);
        List<List<Integer>> chows = List.of();
        if (QaWinDetector.canWin(append(hand, tile), table.jokerRule)) {
            legal.add("HU");
        }
        if (QaMeldCandidates.canExposedKong(hand, tile, table.jokerRule)) {
            legal.add("KONG:EXPOSED:" + tile);
        }
        if (QaMeldCandidates.canPung(hand, tile, table.jokerRule)) {
            legal.add("PUNG:" + tile);
        }
        if (seat == table.nextSeat(discarder)) {
            chows = QaMeldCandidates.chowCandidates(hand, tile, table.jokerRule);
            for (int index = 0; index < chows.size(); index++) {
                legal.add("CHOW:" + index);
            }
        }
        legal.add("PASS");
        if (legal.size() == 1) {
            return parse("PASS");
        }
        String fallback = legal.contains("HU") ? "HU" : "PASS";
        String state = tableState(table, seat, tile) + ";chowCandidates=" + namedChows(chows);
        return parse(choose(table, state, legal, fallback));
    }

    Decision decideRobKong(QaRoundTable table, int seat, int tile) {
        List<String> legal = List.of("HU", "PASS");
        return parse(choose(table, tableState(table, seat, tile), legal, "HU"));
    }

    /** 客户端播放用思考时长；不阻塞后端事务。 */
    static long thinkingDelayMillis(QaRoundTable table) {
        return QaBotThinkingRhythm.thinkingDelayMillis(table);
    }

    private String choose(
            QaRoundTable table, String state, List<String> legal, String fallback) {
        if (!table.goldMode) {
            return fallback;
        }
        try {
            return decisionSource
                    .choose(state, List.copyOf(legal))
                    .filter(legal::contains)
                    .orElse(fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String fallbackTurn(List<String> legal) {
        if (legal.contains("HU")) {
            return "HU";
        }
        return legal.stream()
                .filter(action -> action.startsWith("DISCARD:"))
                .max(Comparator.comparingInt(QaTaizhouBotPolicy::lastNumber))
                .orElse(legal.get(0));
    }

    private static Decision parse(String choice) {
        String[] parts = choice.split(":");
        return switch (parts[0]) {
            case "DISCARD" -> new Decision(Action.DISCARD, Integer.parseInt(parts[1]), null, -1);
            case "CHOW" -> new Decision(Action.CHOW, 0, null, Integer.parseInt(parts[1]));
            case "PUNG" -> new Decision(Action.PUNG, Integer.parseInt(parts[1]), null, -1);
            case "KONG" ->
                    new Decision(Action.KONG, Integer.parseInt(parts[2]), parts[1], -1);
            case "HU" -> new Decision(Action.HU, 0, null, -1);
            case "PASS" -> new Decision(Action.PASS, 0, null, -1);
            default -> throw new IllegalStateException("unknown server legal action");
        };
    }

    private static int lastNumber(String action) {
        return Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
    }

    private static int drawnTile(QaRoundTable table, int seat) {
        return table.hasDrawnTile(seat) ? table.drawnTile : QaTaizhouTiles.NO_TILE;
    }

    private static List<List<Integer>> pongMelds(QaRoundTable table, int seat) {
        return table.melds().get(seat).stream()
                .filter(meld -> "PONG".equals(meld.combType()))
                .map(QaRoundTable.Meld::tiles)
                .toList();
    }

    private static String tableState(QaRoundTable table, int seat, Integer contextTile) {
        return "台州麻将;seat="
                + seat
                + ";hand="
                + namedTiles(table.hands().get(seat))
                + ";joker="
                + tileName(table.jokerRule.jokerTile())
                + ";contextTile="
                + (contextTile == null ? "无" : tileName(contextTile))
                + ";wallRemaining="
                + table.wall.size()
                + ";rivers="
                + table.rivers()
                + ";melds="
                + table.melds();
    }

    private static List<String> namedTiles(List<Integer> tiles) {
        return tiles.stream().map(QaTaizhouBotPolicy::tileName).toList();
    }

    private static List<List<String>> namedChows(List<List<Integer>> chows) {
        return chows.stream().map(QaTaizhouBotPolicy::namedTiles).toList();
    }

    private static String tileName(int tile) {
        int rank = QaTaizhouTiles.rankOf(tile);
        String name =
                switch (QaTaizhouTiles.suitOf(tile)) {
                    case QaTaizhouTiles.SUIT_WAN -> rank + "万";
                    case QaTaizhouTiles.SUIT_TIAO -> rank + "条";
                    case QaTaizhouTiles.SUIT_TONG -> rank + "筒";
                    case QaTaizhouTiles.SUIT_FENG -> List.of("", "东", "南", "西", "北").get(rank);
                    case QaTaizhouTiles.SUIT_JIAN -> List.of("", "中", "发", "白").get(rank);
                    default -> "未知";
                };
        return name + "(" + tile + ")";
    }

    private static List<Integer> append(List<Integer> hand, int tile) {
        List<Integer> all = new ArrayList<>(hand);
        all.add(tile);
        return all;
    }
}
