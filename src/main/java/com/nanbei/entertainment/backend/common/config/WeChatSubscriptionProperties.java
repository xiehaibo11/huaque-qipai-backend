package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.wechat.subscription")
public record WeChatSubscriptionProperties(
        boolean enabled, String templateId, int scene) {
    public static final String TEMPLATE_ID =
            "y4Nkx2Owlb1z6F0PuTMxJMMrSWkm0jlG661bLkGOErA";

    public boolean configured() {
        return enabled && TEMPLATE_ID.equals(templateId) && scene == 1000;
    }
}
