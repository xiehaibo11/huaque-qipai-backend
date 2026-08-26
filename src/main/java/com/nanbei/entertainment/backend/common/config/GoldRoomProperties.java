package com.nanbei.entertainment.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 金币匹配房兑底参数：超时未凑满即解散，避免幽灵占位永驻（原版由 MatchServer 服务端队列
 * 承担超时清理，这里以可配置 sweep 实现）。
 */
@ConfigurationProperties("nanbei.gameplay.gold-room")
public record GoldRoomProperties(Duration matchTimeout) {

    public GoldRoomProperties {
        if (matchTimeout == null || matchTimeout.isNegative() || matchTimeout.isZero()) {
            matchTimeout = Duration.ofSeconds(120);
        }
    }
}
