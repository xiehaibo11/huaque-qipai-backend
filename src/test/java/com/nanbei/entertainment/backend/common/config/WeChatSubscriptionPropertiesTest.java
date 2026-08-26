package com.nanbei.entertainment.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WeChatSubscriptionPropertiesTest {
    @Test
    void enabledConfigurationRequiresFixedTemplateAndScene() {
        assertThat(
                        new WeChatSubscriptionProperties(
                                        true,
                                        WeChatSubscriptionProperties.TEMPLATE_ID,
                                        1000)
                                .configured())
                .isTrue();
        assertThat(
                        new WeChatSubscriptionProperties(
                                        false,
                                        WeChatSubscriptionProperties.TEMPLATE_ID,
                                        1000)
                                .configured())
                .isFalse();
        assertThat(new WeChatSubscriptionProperties(true, "other", 1000).configured())
                .isFalse();
        assertThat(
                        new WeChatSubscriptionProperties(
                                        true,
                                        WeChatSubscriptionProperties.TEMPLATE_ID,
                                        999)
                                .configured())
                .isFalse();
    }

    @Test
    void applicationConfigurationExposesTemplateEnvironmentVariable() throws Exception {
        String yaml =
                Files.readString(
                        Path.of("src/main/resources/application.yml"),
                        StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains(
                        "template-id: ${WECHAT_SUBSCRIPTION_TEMPLATE_ID:"
                                + WeChatSubscriptionProperties.TEMPLATE_ID
                                + "}");
    }
}
