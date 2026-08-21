package com.nanbei.entertainment.backend.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopMigrationContractTest {
    @Test
    void migrationDefinesCatalogInventoryPurchasesCouponsAndOriginalProducts()
            throws Exception {
        Path migration =
                Path.of("src/main/resources/db/migration/V16__native_shop.sql");
        String sql =
                new String(Files.readAllBytes(migration), StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE shop_products");
        assertThat(sql).contains("CREATE TABLE shop_product_rewards");
        assertThat(sql).contains("CREATE TABLE shop_purchase_records");
        assertThat(sql).contains("CREATE TABLE shop_inventory_items");
        assertThat(sql).contains("ADD COLUMN coupons");
        assertThat(sql).contains("'DIAMOND_12800'");
        assertThat(sql).contains("'COIN_9280000'");
        assertThat(sql).contains("'PROP_GOLD_CARD_10'");
        assertThat(sql).contains("'COUPON_ROOM_CARD_50'");
        assertThat(sql).contains("'SHOP_RECORDER_DAY'");
        assertThat(sql).contains("lifetime_limit");

        Path sevenDayMigration =
                Path.of(
                        "src/main/resources/db/migration/"
                                + "V17__add_native_shop_seven_day_membership.sql");
        String sevenDaySql =
                new String(
                        Files.readAllBytes(sevenDayMigration),
                        StandardCharsets.UTF_8);
        assertThat(sevenDaySql).contains("'SXVIP_7_DAYS'");
        assertThat(sevenDaySql).contains("'time_membership'");
        assertThat(sevenDaySql).contains("2500");
        assertThat(sevenDaySql).contains("'MEMBERSHIP_DAY'");

        Path recorderMigration =
                Path.of(
                        "src/main/resources/db/migration/"
                                + "V19__add_original_recorder_products.sql");
        String recorderSql =
                new String(
                        Files.readAllBytes(recorderMigration),
                        StandardCharsets.UTF_8);
        assertThat(recorderSql)
                .contains(
                        "DROP CONSTRAINT ck_shop_price_currency",
                        "ADD CONSTRAINT ck_shop_price_currency",
                        "'CNY', 'DIAMOND', 'ROOM_CARD', 'COUPON', 'FREE'",
                        "'PROP_RECORDER_2_HOURS'",
                        "'PROP_RECORDER_1_DAY'",
                        "'PROP_RECORDER_3_DAYS'",
                        "'PROP_RECORDER_7_DAYS'",
                        "'PROP_RECORDER_1_ROUND'",
                        "'PROP_RECORDER_10_ROUNDS'",
                        "'PROP_RECORDER_20_ROUNDS'",
                        "'ROOM_CARD'",
                        "'DIAMOND'",
                        "'recorder'");
    }
}
