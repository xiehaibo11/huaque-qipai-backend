package com.nanbei.entertainment.backend.room.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public final class RoomRuleAssembler {
    public RoomRuleSelection assemble(
            JsonNode config, int categoryIndex, List<String> selectedNodeNames) {
        if (config == null || !config.isObject()) {
            throw invalid("规则配置无效");
        }
        if (selectedNodeNames == null) {
            throw invalid("已选规则不能为空");
        }
        Set<String> selected = new HashSet<>(selectedNodeNames);
        if (selected.size() != selectedNodeNames.size()) {
            throw invalid("已选规则不能重复");
        }

        List<JsonNode> groups = groups(config, categoryIndex);
        Map<String, JsonNode> knownPrimaryNodes = new HashMap<>();
        Set<String> knownDropdownNodes = new HashSet<>();
        for (JsonNode group : groups) {
            for (JsonNode option : options(group)) {
                knownPrimaryNodes.put(requiredText(option, "node"), option);
                JsonNode dropdown = option.path("dropdown");
                if (dropdown.isArray()) {
                    for (JsonNode item : dropdown) {
                        knownDropdownNodes.add(requiredText(item, "node"));
                    }
                }
            }
        }
        for (String node : selected) {
            if (!knownPrimaryNodes.containsKey(node)
                    && !knownDropdownNodes.contains(node)) {
                throw invalid("包含未知规则选项: " + node);
            }
            JsonNode option = knownPrimaryNodes.get(node);
            if (option != null
                    && option.has("categoryIndex")
                    && option.path("categoryIndex").asInt() != categoryIndex) {
                throw invalid("所选玩法与规则分类不一致: " + node);
            }
        }

        Set<Integer> activeLevels = activeLevels(knownPrimaryNodes, selected);
        Map<String, OptionState> states = optionStates(knownPrimaryNodes, selected, activeLevels);

        List<String> radioRules = new ArrayList<>();
        List<String> checkboxRules = new ArrayList<>();
        List<String> mustExistRules = new ArrayList<>();
        List<String> displayRules = new ArrayList<>();
        Set<String> consumed = new HashSet<>();
        int playerCount = 4;
        int playCount = 8;
        RoomPayType payType = RoomPayType.ALL;
        int selectedPlayModeIndex = 1;
        int playModeOptionCount = 1;
        int selectedPayModeIndex = 0;

        for (JsonNode group : groups) {
            String type = requiredText(group, "type");
            List<JsonNode> options = options(group);
            if ("RADIO".equals(type)) {
                List<JsonNode> visibleOptions =
                        options.stream()
                                .filter(option -> states.get(option.path("node").asText()).visible())
                                .toList();
                if (visibleOptions.isEmpty()) {
                    continue;
                }
                List<JsonNode> selectedOptions =
                        visibleOptions.stream()
                                .filter(option -> selected.contains(option.path("node").asText()))
                                .toList();
                if (selectedOptions.size() != 1) {
                    throw invalid("每个单选规则必须且只能选择一项: " + group.path("key").asText());
                }
                JsonNode option = selectedOptions.getFirst();
                consumed.add(requiredText(option, "node"));
                String counter = group.path("counter").asText("");
                switch (counter) {
                    case "PLAYER_COUNT" -> playerCount = requiredPositiveInt(option, "value");
                    case "PLAY_COUNT" -> {
                        playCount = requiredPositiveInt(option, "value");
                        selectedPlayModeIndex = visibleOptions.indexOf(option) + 1;
                        playModeOptionCount = visibleOptions.size();
                    }
                    case "PAY_TYPE" -> {
                        payType = parsePayType(requiredText(option, "costType"));
                        selectedPayModeIndex = payType == RoomPayType.AA ? 1 : 0;
                        radioRules.add(requiredText(option, "node"));
                    }
                    default -> {
                        radioRules.add(selectedRadioRule(option, selected, consumed));
                        displayRules.add(selectedDisplayText(option, selected));
                    }
                }
            } else if ("CHECKBOX".equals(type)) {
                for (JsonNode option : options) {
                    String node = requiredText(option, "node");
                    OptionState state = states.get(node);
                    if (!state.visible()) {
                        continue;
                    }
                    boolean checked = selected.contains(node);
                    if (checked) {
                        consumed.add(node);
                        displayRules.add(requiredText(option, "text"));
                    }
                    String field = checked ? "select" : "unselect";
                    if (option.hasNonNull(field)) {
                        checkboxRules.add(option.path(field).asText());
                    }
                }
            } else {
                throw invalid("不支持的规则类型: " + type);
            }
        }

        for (JsonNode group : groups) {
            for (JsonNode option : options(group)) {
                String node = requiredText(option, "node");
                if (!states.get(node).visible() || !option.hasNonNull("mustExistRule")) {
                    continue;
                }
                String field = selected.contains(node) ? "mustExistRuleYes" : "mustExistRuleNo";
                if (option.hasNonNull(field)) {
                    mustExistRules.add(option.path(field).asText());
                }
            }
        }

        if (!consumed.equals(selected)) {
            Set<String> unconsumed = new HashSet<>(selected);
            unconsumed.removeAll(consumed);
            throw invalid("包含未消费的规则选项: " + String.join(",", unconsumed));
        }

        JsonNode costOption = selectedCostOption(knownPrimaryNodes, states, selected);
        int roomFeeCenti = cost(costOption, payType, playerCount, config);
        int roomMode =
                selectedPayModeIndex * roomModeStride(config, playModeOptionCount)
                        + selectedPlayModeIndex;
        Map<String, String> roomConditions = roomConditions(groups, states, selected);
        String gameRule =
                String.join("", radioRules)
                        + String.join("", checkboxRules)
                        + String.join("", mustExistRules)
                        + config.path("trailingRule").asText("")
                        + "RoomFee='"
                        + displayCenti(roomFeeCenti)
                        + "';";
        return new RoomRuleSelection(
                gameRule,
                String.join("/", displayRules),
                playerCount,
                playCount,
                payType,
                roomFeeCenti,
                roomMode,
                roomConditions);
    }

    private static List<JsonNode> groups(JsonNode config, int categoryIndex) {
        JsonNode categories = config.path("categories");
        JsonNode groups;
        if (categories.isArray()) {
            JsonNode category = null;
            for (JsonNode candidate : categories) {
                if (candidate.path("index").asInt() == categoryIndex) {
                    category = candidate;
                    break;
                }
            }
            if (category == null) {
                throw invalid("规则分类不存在: " + categoryIndex);
            }
            groups = category.path("groups");
        } else {
            if (categoryIndex != 1) {
                throw invalid("规则分类不存在: " + categoryIndex);
            }
            groups = config.path("groups");
        }
        if (!groups.isArray() || groups.isEmpty()) {
            throw invalid("规则配置没有选项");
        }
        List<JsonNode> result = new ArrayList<>();
        groups.forEach(result::add);
        result.sort(Comparator.comparingInt(group -> group.path("order").asInt()));
        return result;
    }

    private static Set<Integer> activeLevels(
            Map<String, JsonNode> knownPrimaryNodes, Set<String> selected) {
        Set<Integer> active = new HashSet<>();
        for (String node : selected) {
            JsonNode option = knownPrimaryNodes.get(node);
            if (option != null && option.has("linkageLevel")) {
                active.add(option.path("linkageLevel").asInt());
            }
        }
        for (Map.Entry<String, JsonNode> entry : knownPrimaryNodes.entrySet()) {
            JsonNode option = entry.getValue();
            if (!selected.contains(entry.getKey()) && option.has("unSelectlinkageLevel")) {
                active.add(option.path("unSelectlinkageLevel").asInt());
            }
        }
        return active;
    }

    private static Map<String, OptionState> optionStates(
            Map<String, JsonNode> knownPrimaryNodes,
            Set<String> selected,
            Set<Integer> activeLevels) {
        Map<String, OptionState> states = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : knownPrimaryNodes.entrySet()) {
            String node = entry.getKey();
            JsonNode option = entry.getValue();
            JsonNode show = option.path("show");
            boolean visible =
                    (!show.isArray() || show.isEmpty() || intersects(show, activeLevels))
                            && !intersects(option.path("hide"), activeLevels);
            boolean forcedSelected =
                    intersects(option.path("prohibitAndSelect"), activeLevels)
                            || intersects(option.path("linkSelect"), activeLevels);
            boolean prohibited =
                    intersects(option.path("prohibit"), activeLevels)
                            || intersects(option.path("linkProhibit"), activeLevels)
                            || containsAll(option.path("prohibitMeanwhile"), activeLevels);
            boolean forcedUnselected = intersects(option.path("linkUnSelect"), activeLevels);
            if (!visible && selected.contains(node)) {
                throw invalid("不可见规则不能被选择: " + node);
            }
            if ((prohibited || forcedUnselected) && selected.contains(node)) {
                throw invalid("禁用规则不能被选择: " + node);
            }
            if (forcedSelected && !prohibited && !selected.contains(node)) {
                throw invalid("联动规则必须选中: " + node);
            }
            states.put(node, new OptionState(visible));
        }
        return states;
    }

    private static boolean intersects(JsonNode levels, Set<Integer> activeLevels) {
        if (!levels.isArray()) {
            return false;
        }
        for (JsonNode level : levels) {
            if (activeLevels.contains(level.asInt())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAll(JsonNode levels, Set<Integer> activeLevels) {
        if (!levels.isArray() || levels.isEmpty()) {
            return false;
        }
        for (JsonNode level : levels) {
            if (!activeLevels.contains(level.asInt())) {
                return false;
            }
        }
        return true;
    }

    private static List<JsonNode> options(JsonNode group) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode lines = group.path("lines");
        if (!lines.isArray()) {
            throw invalid("规则行配置无效: " + group.path("key").asText());
        }
        for (JsonNode line : lines) {
            JsonNode options = line.path("options");
            if (options.isArray()) {
                options.forEach(result::add);
            }
        }
        if (result.isEmpty()) {
            throw invalid("规则组没有选项: " + group.path("key").asText());
        }
        return result;
    }

    private static String selectedRadioRule(
            JsonNode option, Set<String> selected, Set<String> consumed) {
        JsonNode dropdown = option.path("dropdown");
        if (!dropdown.isArray()) {
            return requiredText(option, "node");
        }
        List<String> selectedDropdowns = new ArrayList<>();
        for (JsonNode item : dropdown) {
            String node = requiredText(item, "node");
            if (selected.contains(node)) {
                selectedDropdowns.add(node);
            }
        }
        if (selectedDropdowns.size() != 1) {
            throw invalid("下拉规则必须且只能选择一项: " + option.path("node").asText());
        }
        consumed.add(selectedDropdowns.getFirst());
        return selectedDropdowns.getFirst();
    }

    private static String selectedDisplayText(JsonNode option, Set<String> selected) {
        JsonNode dropdown = option.path("dropdown");
        if (dropdown.isArray()) {
            for (JsonNode item : dropdown) {
                if (selected.contains(requiredText(item, "node"))) {
                    return requiredText(item, "text");
                }
            }
        }
        return requiredText(option, "text");
    }

    private static int cost(
            JsonNode costOption,
            RoomPayType payType,
            int playerCount,
            JsonNode config) {
        JsonNode costs = costOption.path("costs").path(payType.name());
        String key = config.path("costRelativeToPlayers").asBoolean() ? Integer.toString(playerCount) : "0";
        JsonNode value = costs.get(key);
        if ((value == null || !value.canConvertToInt()) && !"0".equals(key)) {
            value = costs.get("0");
        }
        if (value == null || !value.canConvertToInt() || value.asInt() < 0) {
            throw invalid("所选人数、局数和支付方式没有房卡价格");
        }
        return value.asInt();
    }

    private static int roomModeStride(JsonNode config, int playModeOptionCount) {
        JsonNode explicit = config.get("roomModeStride");
        if (explicit != null && explicit.canConvertToInt() && explicit.asInt() > 0) {
            return explicit.asInt();
        }
        return Math.max(4, playModeOptionCount);
    }

    private static Map<String, String> roomConditions(
            List<JsonNode> groups,
            Map<String, OptionState> states,
            Set<String> selected) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode group : groups) {
            for (JsonNode option : options(group)) {
                String node = requiredText(option, "node");
                if (!states.get(node).visible() || !option.hasNonNull("condition")) {
                    continue;
                }
                String valueField = selected.contains(node) ? "conditionYes" : "conditionNo";
                JsonNode value = option.get(valueField);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    result.put(requiredText(option, "condition"), value.asText());
                }
            }
        }
        return Map.copyOf(result);
    }

    private static JsonNode selectedCostOption(
            Map<String, JsonNode> knownPrimaryNodes,
            Map<String, OptionState> states,
            Set<String> selected) {
        JsonNode result = null;
        for (String node : selected) {
            JsonNode option = knownPrimaryNodes.get(node);
            if (option == null || !states.get(node).visible() || !option.path("costs").isObject()) {
                continue;
            }
            if (result != null) {
                throw invalid("规则包含多个房卡价格选项");
            }
            result = option;
        }
        if (result == null) {
            throw invalid("所选规则没有房卡价格");
        }
        return result;
    }

    private static RoomPayType parsePayType(String value) {
        try {
            return RoomPayType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("不支持的支付方式: " + value);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("规则字段缺失: " + field);
        }
        return value.asText();
    }

    private static int requiredPositiveInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt() || value.asInt() <= 0) {
            throw invalid("规则字段无效: " + field);
        }
        return value.asInt();
    }

    private static String displayCenti(int centi) {
        return BigDecimal.valueOf(centi, 2).stripTrailingZeros().toPlainString();
    }

    private static RoomRuleValidationException invalid(String message) {
        return new RoomRuleValidationException(message);
    }

    private record OptionState(boolean visible) {}
}
