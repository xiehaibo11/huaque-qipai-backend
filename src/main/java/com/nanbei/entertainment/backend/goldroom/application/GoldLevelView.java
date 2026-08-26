package com.nanbei.entertainment.backend.goldroom.application;

import com.nanbei.entertainment.backend.goldroom.domain.GoldGameLevelEntity;

/**
 * One gold-room level as served to the client.
 *
 * <p>Only raw values are returned. The 底分 and 准入 strings are formatted on the client, which
 * reproduces the original {@code ChooseRoom.lua} rules (the trailing 以上 for {@code dynamicCost}
 * and for {@code maxRich == -1}, plus the 万/亿 folding of {@code getRichString}). Keeping the
 * formatting in one place avoids a second source of truth for原版文案.
 */
public record GoldLevelView(
        int roomNameFlag,
        int uiType,
        int chairCount,
        long baseScore,
        boolean dynamicCost,
        long minRich,
        long maxRich,
        long onlineCount,
        String tagLeftTop,
        String tagRightTop,
        String tagRibbon1,
        String tagRibbon2) {

    /** Original palette index: {@code UIType = Level % 10}, clamped to the five card skins. */
    public static int uiTypeOf(int roomNameFlag) {
        int uiType = roomNameFlag % 10;
        if (uiType > 5) {
            return 5;
        }
        return uiType < 1 ? 1 : uiType;
    }

    public static GoldLevelView from(GoldGameLevelEntity entity, long onlineCount) {
        int flag = entity.getId().getRoomNameFlag();
        return new GoldLevelView(
                flag,
                uiTypeOf(flag),
                entity.getChairCount(),
                entity.getBaseScore(),
                entity.isDynamicCost(),
                entity.getMinRich(),
                entity.getMaxRich(),
                onlineCount,
                entity.getTagLeftTop(),
                entity.getTagRightTop(),
                entity.getTagRibbon1(),
                entity.getTagRibbon2());
    }
}
