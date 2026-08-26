package com.nanbei.entertainment.backend.wechatsubscription.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.common.error.GlobalExceptionHandler;
import com.nanbei.entertainment.backend.common.security.SecurityConfiguration;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionCompleteResponse;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionCompletion;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionIntentResponse;
import com.nanbei.entertainment.backend.wechatsubscription.application.WeChatSubscriptionIntentService;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = WeChatSubscriptionController.class,
        properties = "nanbei.security.jwt-secret=01234567890123456789012345678901")
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties(SecurityProperties.class)
class WeChatSubscriptionControllerWebMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INTENT_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @MockitoBean WeChatSubscriptionIntentService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void authenticate() {
        when(jwtDecoder.decode("subscription-token"))
                .thenReturn(
                        Jwt.withTokenValue("subscription-token")
                                .header("alg", "HS256")
                                .subject(USER_ID.toString())
                                .build());
    }

    @Test
    void createRequiresJwtAndUsesAuthenticatedUser() throws Exception {
        when(service.create(USER_ID))
                .thenReturn(
                        new WeChatSubscriptionIntentResponse(
                                INTENT_ID,
                                WeChatSubscriptionProperties.TEMPLATE_ID,
                                1000,
                                "reserved",
                                Instant.parse("2026-08-25T12:10:00Z")));

        mockMvc.perform(post("/api/v1/wechat/subscriptions/intents"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/wechat/subscriptions/intents")
                                .header("Authorization", "Bearer subscription-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intentId").value(INTENT_ID.toString()))
                .andExpect(jsonPath("$.scene").value(1000));

        verify(service).create(USER_ID);
    }

    @Test
    void completeUsesSdkCallbackBoundToJwtUser() throws Exception {
        WeChatSubscriptionCompletion completion =
                new WeChatSubscriptionCompletion(
                        0,
                        "confirm",
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        1000,
                        "reserved",
                        "openid-1",
                        "transaction-1");
        when(service.complete(USER_ID, INTENT_ID, completion))
                .thenReturn(
                        new WeChatSubscriptionCompleteResponse(
                                WeChatSubscriptionGrantStatus.AVAILABLE));

        mockMvc.perform(
                        post(
                                        "/api/v1/wechat/subscriptions/intents/"
                                                + INTENT_ID
                                                + "/complete")
                                .header("Authorization", "Bearer subscription-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "errCode": 0,
                                          "action": "confirm",
                                          "templateId": "%s",
                                          "scene": 1000,
                                          "reserved": "reserved",
                                          "openId": "openid-1",
                                          "transaction": "transaction-1"
                                        }
                                        """
                                                .formatted(
                                                        WeChatSubscriptionProperties.TEMPLATE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(service).complete(USER_ID, INTENT_ID, completion);
    }
}
