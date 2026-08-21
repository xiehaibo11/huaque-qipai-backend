package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

class WeChatOpenApiClientWiringTest {
    @Test
    void createsProductionClientThroughSpringConstructorInjection() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            assertThat(context.getBean(WeChatOpenApiClient.class)).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(WeChatOpenApiClient.class)
    static class TestConfiguration {
        @Bean
        WeChatProperties weChatProperties() {
            return new WeChatProperties(
                    false,
                    "",
                    "",
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(5));
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
