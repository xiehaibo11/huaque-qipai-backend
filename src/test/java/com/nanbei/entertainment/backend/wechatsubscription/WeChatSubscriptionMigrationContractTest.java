package com.nanbei.entertainment.backend.wechatsubscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WeChatSubscriptionMigrationContractTest {
    private static final Path MIGRATION =
            Path.of(
                    "src/main/resources/db/migration/"
                            + "V53__wechat_one_time_subscriptions.sql");

    @Test
    void migrationDefinesOneTimeGrantAndDeliveryInvariants() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE wechat_subscription_grants");
        assertThat(sql).contains("reserved_hash CHAR(64) NOT NULL UNIQUE");
        assertThat(sql).contains("openid_subject_hash CHAR(64) NOT NULL");
        assertThat(sql).contains("CREATE TABLE wechat_subscription_deliveries");
        assertThat(sql).contains("grant_id UUID NOT NULL UNIQUE");
        assertThat(sql)
                .contains(
                        "UNIQUE (user_id, template_id, event_type, event_id)");
        assertThat(sql)
                .contains(
                        "'AVAILABLE'",
                        "'DENIED'",
                        "'CANCELLED'",
                        "'EXPIRED'",
                        "'CLAIMED'",
                        "'INVALIDATED'",
                        "'SENDING'",
                        "'RETRYABLE'",
                        "'AMBIGUOUS'");
        assertThat(sql).doesNotContain("reserved VARCHAR", "openid VARCHAR");
    }
}
