package com.nanbei.entertainment.backend.room.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Formats the 30109 rule string in the order used by the recovered Taizhou Lua client. */
public final class TaizhouMahjongRuleDisplay {
    private TaizhouMahjongRuleDisplay() {}

    public static String render(
            String gameRule, int playerCount, int playCount, RoomPayType payType) {
        Map<String, String> rules = parse(gameRule);
        List<String> display = new ArrayList<>();
        addMapped(display, rules, "winLostType", Map.of("1", "不平搓", "2", "平搓"));
        addMapped(display, rules, "forceGPS", Map.of("1", "防作弊"));
        addMapped(display, rules, "liaoDaZiBaoPai", Map.of("1", "撩搭子包牌"));
        addMapped(display, rules, "lianZhuang", Map.of("1", "连庄"));
        addMapped(display, rules, "duiDuiHuFourScore", Map.of("1", "对对胡4胡"));
        addMapped(display, rules, "noShengPaiJieDuan", Map.of("1", "无生牌阶段"));
        addMapped(display, rules, "buSiBao", Map.of("1", "不死包"));
        addMapped(display, rules, "DelColor", Map.of("1", "缺一色", "2", "缺二色"));
        addMapped(
                display,
                rules,
                "FengDing",
                Map.of("0", "不封顶", "60", "60封顶", "80", "80封顶"));

        RoomPayType resolvedPayType = payType;
        String protocolPayType = rules.get("PayType");
        if ("1".equals(protocolPayType) || "7".equals(protocolPayType)) {
            resolvedPayType = RoomPayType.AA;
        } else if ("0".equals(protocolPayType)) {
            resolvedPayType = RoomPayType.ALL;
        }
        display.add(resolvedPayType == RoomPayType.AA ? "平摊消耗" : "房主消耗");
        display.add(playerCount + "人");

        String baseScore = rules.get("basescore");
        if (baseScore != null && !baseScore.isBlank()) {
            display.add("底分" + baseScore);
        }
        display.add(playCount + (playerCount == 2 ? "局" : "圈"));

        int trustSeconds = integer(rules.get("IsSysTrust"));
        if (trustSeconds > 0) {
            display.add("超时" + trustSeconds + "秒托管");
        }
        return String.join("/", display);
    }

    public static boolean isAutoReady(String gameRule) {
        if (gameRule == null || gameRule.isBlank()) {
            return false;
        }
        if ("1".equals(parse(gameRule).get("autoReady"))) {
            return true;
        }
        String compactRule = gameRule.replace(" ", "");
        return compactRule.contains("UserRule='AutoReady=true;'")
                || compactRule.contains("UserRule=\"AutoReady=true;\"");
    }

    private static Map<String, String> parse(String gameRule) {
        Map<String, String> result = new LinkedHashMap<>();
        if (gameRule == null || gameRule.isBlank()) {
            return result;
        }
        for (String assignment : gameRule.split(";")) {
            int separator = assignment.indexOf('=');
            if (separator <= 0 || separator == assignment.length() - 1) {
                continue;
            }
            String key = assignment.substring(0, separator).trim();
            String value = assignment.substring(separator + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("'") && value.endsWith("'"))
                            || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static void addMapped(
            List<String> display,
            Map<String, String> rules,
            String key,
            Map<String, String> labels) {
        String value = rules.get(key);
        if (value == null) {
            return;
        }
        String label = labels.get(value);
        if (label != null) {
            display.add(label);
        }
    }

    private static int integer(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
