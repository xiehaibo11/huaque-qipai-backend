package com.nanbei.entertainment.backend.common.config;

import com.aliyun.teaopenapi.models.Config;
import com.nanbei.entertainment.backend.auth.infrastructure.AliyunSmsGateway;
import com.nanbei.entertainment.backend.auth.infrastructure.SmsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
@ConditionalOnProperty(prefix = "nanbei.sms", name = "enabled", havingValue = "true")
public class AliyunSmsConfiguration {
    private static final String SMS_ENDPOINT = "dysmsapi.aliyuncs.com";

    @Bean
    com.aliyun.dysmsapi20170525.Client aliyunSmsClient(SmsProperties properties)
            throws Exception {
        com.aliyun.credentials.Client credentials = new com.aliyun.credentials.Client();
        Config config =
                new Config()
                        .setCredential(credentials)
                        .setEndpoint(SMS_ENDPOINT)
                        .setRegionId(properties.regionId());
        return new com.aliyun.dysmsapi20170525.Client(config);
    }

    @Bean
    SmsGateway smsGateway(com.aliyun.dysmsapi20170525.Client client) {
        return new AliyunSmsGateway(client);
    }
}
