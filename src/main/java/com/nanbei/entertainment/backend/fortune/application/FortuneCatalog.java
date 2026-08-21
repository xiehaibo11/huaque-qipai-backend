package com.nanbei.entertainment.backend.fortune.application;

import java.util.List;

final class FortuneCatalog {
    static final List<FortunePrayerProduct> PRAYERS =
            List.of(
                    prayer("PRAYER_WASH", "洗手", 10, 10, 10),
                    prayer("PRAYER_TREE", "招财树", 20, 25, 5),
                    prayer("PRAYER_POT", "聚宝盆", 30, 30, 15),
                    prayer("PRAYER_MAZU", "妈祖", 40, 15, 35),
                    prayer("PRAYER_BEAD", "转运珠", 50, 10, 50),
                    prayer("PRAYER_LUCK", "大吉大利", 60, 40, 40),
                    prayer("PRAYER_PIXIU", "貔貅", 70, 60, 20),
                    prayer("PRAYER_CAT", "招财猫", 80, 70, 25),
                    prayer("PRAYER_DRAGON", "金龙", 100, 80, 40),
                    prayer("PRAYER_PHOENIX", "金凤", 120, 40, 90),
                    prayer("PRAYER_PAIR", "龙飞凤舞", 150, 100, 100),
                    prayer("PRAYER_WIND", "顺风", 180, 80, 140));

    static final List<FortuneTreasureProduct> TREASURES =
            List.of(
                    treasure(1, "手串", "NORMAL", 60),
                    treasure(2, "宝瓶", "NORMAL", 60),
                    treasure(3, "金元宝", "NORMAL", 60),
                    treasure(4, "玉佩", "NORMAL", 60),
                    treasure(5, "宝石戒指", "GOOD", 80),
                    treasure(6, "聚宝葫芦", "GOOD", 80),
                    treasure(7, "金算盘", "GOOD", 80),
                    treasure(8, "金猪拱财", "GOOD", 80),
                    treasure(9, "铜钱串", "SUPER", 168),
                    treasure(10, "阴阳宝镜", "SUPER", 168),
                    treasure(11, "转运珠", "SUPER", 168),
                    treasure(12, "玉如意", "SUPER", 168),
                    treasure(13, "招财金猫", "SPECIAL", 666),
                    treasure(14, "金钱树", "SPECIAL", 666),
                    treasure(15, "聚宝盆", "SPECIAL", 666),
                    treasure(16, "金蟾吐宝", "SPECIAL", 666));

    static final long TREASURE_ONE_DRAW_PRICE_DIAMONDS = 100;
    static final long TREASURE_FIVE_DRAW_PRICE_DIAMONDS = 450;
    static final int TREASURE_FIVE_DRAW_DISCOUNT_TENTHS = 9;

    static final List<FortuneCaishenProduct> CAISHEN =
            List.of(
                    new FortuneCaishenProduct("CAISHEN_1H", "金蟾纳福", 60, 3_600),
                    new FortuneCaishenProduct("CAISHEN_8H", "招财树", 180, 28_800),
                    new FortuneCaishenProduct("CAISHEN_24H", "拜财神", 520, 86_400));

    static final int[] QUANTITY_BASIS_POINTS =
            {10_000, 9_000, 8_500, 8_000, 7_500, 7_000, 6_500, 6_000, 5_500, 5_000};

    private FortuneCatalog() {}

    private static FortunePrayerProduct prayer(
            String code, String name, long price, int wealth, int luck) {
        return new FortunePrayerProduct(code, name, price, wealth, luck);
    }

    private static FortuneTreasureProduct treasure(
            int index, String name, String quality, int score) {
        return new FortuneTreasureProduct(
                String.format("TREASURE_%02d", index), name, quality, score);
    }
}
