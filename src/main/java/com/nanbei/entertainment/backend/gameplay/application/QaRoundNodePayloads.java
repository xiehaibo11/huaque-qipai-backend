package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * {@code qaRound} 节点内 offer 与听牌映射子树的序列化/反序列化
 * （南北自建 QA 状态格式，非原版服务端协议）。从 {@link QaRoundStateCodec} 拆出以控制文件规模。
 */
final class QaRoundNodePayloads {
    private QaRoundNodePayloads() {}

    static Map<String, Object> offerNode(QaRoundTable.PendingOffer offer) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("offerId", offer.offerId);
        node.put("actionToken", offer.actionToken);
        node.put("powerMask", offer.powerMask);
        node.put("contextTile", offer.contextTile);
        node.put("chowCandidates", offer.chowCandidates);
        List<Map<String, Object>> kongOptions = new ArrayList<>();
        for (QaMeldCandidates.KongOption option : offer.kongOptions) {
            kongOptions.add(
                    Map.of("kongType", option.kongType(), "tileValue", option.tileValue()));
        }
        node.put("kongOptions", kongOptions);
        node.put("fromSeat", offer.fromSeat);
        node.put("playOffer", offer.playOffer);
        node.put("claimKind", offer.claimKind == null ? null : offer.claimKind.name());
        node.put("candidateIndex", offer.candidateIndex);
        node.put("passed", offer.passed);
        node.put("offeredAtEpochMilli", offer.offeredAtEpochMilli);
        return node;
    }

    static QaRoundTable.PendingOffer readOffer(JsonNode node) {
        List<List<Integer>> chowCandidates = new ArrayList<>();
        for (JsonNode candidate : node.path("chowCandidates")) {
            List<Integer> tiles = new ArrayList<>();
            candidate.forEach(tile -> tiles.add(tile.asInt()));
            chowCandidates.add(tiles);
        }
        List<QaMeldCandidates.KongOption> kongOptions = new ArrayList<>();
        for (JsonNode option : node.path("kongOptions")) {
            kongOptions.add(
                    new QaMeldCandidates.KongOption(
                            option.path("kongType").asText(), option.path("tileValue").asInt()));
        }
        QaRoundTable.PendingOffer offer =
                new QaRoundTable.PendingOffer(
                        node.path("offerId").asInt(),
                        node.path("actionToken").asText(),
                        node.path("powerMask").asInt(),
                        node.path("contextTile").isNumber()
                                ? node.path("contextTile").asInt()
                                : null,
                        chowCandidates,
                        kongOptions,
                        node.path("fromSeat").asInt(),
                        node.path("playOffer").asBoolean());
        JsonNode claimKind = node.path("claimKind");
        if (claimKind.isTextual()) {
            offer.claimKind = QaClaim.Kind.valueOf(claimKind.asText());
        }
        if (node.path("candidateIndex").isNumber()) {
            offer.candidateIndex = node.path("candidateIndex").asInt();
        }
        offer.passed = node.path("passed").asBoolean();
        if (node.path("offeredAtEpochMilli").isNumber()) {
            offer.offeredAtEpochMilli = node.path("offeredAtEpochMilli").asLong();
        }
        return offer;
    }

    static Map<String, Object> tingInfosNode(Map<Integer, List<QaRoundTable.TingEntry>> tingInfos) {
        Map<String, Object> node = new LinkedHashMap<>();
        tingInfos.forEach(
                (seat, entries) -> {
                    List<Map<String, Object>> payload = new ArrayList<>();
                    for (QaRoundTable.TingEntry entry : entries) {
                        payload.add(
                                Map.of(
                                        "discard", entry.discard(),
                                        "huTargets", entry.huTargets(),
                                        "fanPoints", entry.fanPoints(),
                                        "huPoints", entry.huPoints()));
                    }
                    node.put(Integer.toString(seat), payload);
                });
        return node;
    }

    static void readTingInfos(JsonNode node, Map<Integer, List<QaRoundTable.TingEntry>> target) {
        node.properties()
                .forEach(
                        entry -> {
                            List<QaRoundTable.TingEntry> entries = new ArrayList<>();
                            for (JsonNode tingNode : entry.getValue()) {
                                List<Integer> huTargets = new ArrayList<>();
                                tingNode.path("huTargets")
                                        .forEach(tingTarget -> huTargets.add(tingTarget.asInt()));
                                entries.add(
                                        new QaRoundTable.TingEntry(
                                                tingNode.path("discard").asInt(),
                                                huTargets,
                                                points(tingNode, "fanPoints", huTargets.size()),
                                                points(tingNode, "huPoints", huTargets.size())));
                            }
                            target.put(Integer.parseInt(entry.getKey()), entries);
                        });
    }

    /** 旧状态没有台/胡两列，按目标数补零读回，避免续推老 session 时炸构造。 */
    private static List<Integer> points(JsonNode tingNode, String field, int size) {
        List<Integer> values = new ArrayList<>(size);
        tingNode.path(field).forEach(point -> values.add(point.asInt()));
        while (values.size() < size) {
            values.add(0);
        }
        return values.size() == size ? values : values.subList(0, size);
    }
}
