package com.nanbei.entertainment.backend.common.config;

import com.aliyun.teaopenapi.models.Config;
import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.auth.infrastructure.AliyunDypnsClient;
import com.nanbei.entertainment.backend.auth.infrastructure.AliyunOneTapMobileExchange;
import com.nanbei.entertainment.backend.auth.infrastructure.DypnsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.one-tap",
        name = "enabled",
        havingValue = "true")
public class AliyunOneTapConfiguration {
    @Bean
    com.aliyun.dypnsapi20170525.Client aliyunDypnsSdkClient(
            OneTapProperties properties) throws Exception {
        com.aliyun.credentials.Client credentials =
                new com.aliyun.credentials.Client();
        Config config =
                new Config()
                        .setCredential(credentials)
                        .setEndpoint(properties.endpoint())
                        .setRegionId(properties.regionId())
                        .setConnectTimeout(
                                Math.toIntExact(
                                        properties
                                                .connectTimeout()
                                                .toMillis()))
                        .setReadTimeout(
                                Math.toIntExact(
                                        properties
                                                .readTimeout()
                                                .toMillis()));
        return new com.aliyun.dypnsapi20170525.Client(config);
    }

    @Bean
    DypnsClient dypnsClient(
            com.aliyun.dypnsapi20170525.Client client) {
        return new AliyunDypnsClient(client);
    }

    @Bean
    OneTapMobileExchange oneTapMobileExchange(
            DypnsClient client) {
        return new AliyunOneTapMobileExchange(client);
    }
}
