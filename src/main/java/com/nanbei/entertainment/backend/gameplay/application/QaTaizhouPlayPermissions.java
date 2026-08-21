package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出牌权限投影（南北自建）：按座位下发可打出的手牌原始索引，以及其中「打出后能听」的索引子集。
 *
 * <p>形状对齐 Android {@code GameplayRoundProtocol.parseOptionalPlayPermission}。原始索引约定与
 * {@code QaTaizhouProjection} 的手牌投影一致：索引 {@code 0} 是刚摸进的牌（仅当该座位有摸牌），
 * {@code 1..n} 依次是暗牌区的立牌。
 */
final class QaTaizhouPlayPermissions {
    private QaTaizhouPlayPermissions() {}

    static Map<String, Object> bySeat(QaRoundTable table) {
        Map<String, Object> bySeat = new LinkedHashMap<>();
        for (Map.Entry<Integer, QaRoundTable.PendingOffer> entry : table.offers().entrySet()) {
            QaRoundTable.PendingOffer offer = entry.getValue();
            if (!offer.playOffer || offer.answered()) {
                continue;
            }
            int seat = entry.getKey();
            List<Integer> seatHand = table.hands().get(seat);
            List<Integer> playable = new ArrayList<>();
            List<Integer> ting = new ArrayList<>();
            Set<Integer> tingDiscards = tingDiscardValues(table, seat);
            int concealedCount = seatHand.size();
            if (table.hasDrawnTile(seat)) {
                playable.add(0);
                if (tingDiscards.contains(table.drawnTile)) {
                    ting.add(0);
                }
                concealedCount--;
            }
            List<Integer> concealed = QaTaizhouProjection.concealedTiles(table, seat, seatHand);
            for (int index = 1; index <= concealedCount; index++) {
                playable.add(index);
                if (tingDiscards.contains(concealed.get(index - 1))) {
                    ting.add(index);
                }
            }
            Map<String, Object> permission = new LinkedHashMap<>();
            permission.put("actionToken", offer.actionToken);
            permission.put("mode", "SINGLE_CLICK");
            permission.put("playableOriginalIndexes", playable);
            permission.put("tingOriginalIndexes", ting);
            // 「能碰不碰」高亮在原版是**客户端**逻辑：GameModule:analysePower 读 nPower 位、
            // lightActionMahs 取 getLastPlayMah()，服务端只下发权限与出牌。它还必须在没有出牌权的
            // 碰/明杠待答窗口生效，因此不能挂在出牌权限上——客户端按 actionOffer 的
            // powerMask + contextTile 自行计算（TaizhouMahjongHandRenderer）。此字段保持为空。
            permission.put("actionMaskOriginalIndexes", List.of());
            // 包牌预警色只能由服务端下发的 msgPreBaoPaiMah.nBaoPaiMahs 驱动
            // （TaiZhou/TaiZhouMahjong/.../GameLayer/Module.luac:28-31，30109 的 GameKey 正是
            // "TaiZhou.TaiZhouMahjong"，见 app/Config/GameSub.lua:104）。南北自建后端尚未实现
            // 撩搭子包牌/不死包的包牌判定（见 D3 / BLOCKED-02A），因此如实下发空数组，不臆造。
            permission.put("preBaoOriginalIndexes", List.of());
            bySeat.put(Integer.toString(seat), permission);
        }
        return bySeat;
    }

    /**
     * 「打出后能听」的牌值集合，取自同一回合已算好的 {@code TING_INFO}
     * （{@code QaRoundTurnDriver.appendTingInfo}）。
     *
     * <p>原版 30109 走 {@code msgAllWaitInfo}：{@code TaiZhou/.../GameLayer/Module.luac:106-131} 在
     * {@code getHaveTing()} 打开时把「打出哪几张能听」写进 {@code setTingMahs}，
     * {@code UIMahHandAreaBase.luac:451-458}（{@code showTingInfo}）据此给命中的立牌
     * {@code showTingIcon(true)}，帧名 {@code mahlayer_mah_img_sign.png}。空 {@code huTargets}
     * 不是听，不进集合。
     */
    private static Set<Integer> tingDiscardValues(QaRoundTable table, int seat) {
        List<QaRoundTable.TingEntry> entries = table.tingInfos().get(seat);
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (QaRoundTable.TingEntry tingEntry : entries) {
            if (!tingEntry.huTargets().isEmpty()) {
                values.add(tingEntry.discard());
            }
        }
        return values;
    }
}
