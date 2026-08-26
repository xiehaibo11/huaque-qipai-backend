package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.config.GoldRoomProperties;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 金币匹配房超时兑底调度：应用已开启 @EnableScheduling，这里按配置周期把超时未凑满的
 * 匹配房交给 {@link GoldRoomMatchService#sweepTimedOutRooms} 解散，防止幽灵占位永驻
 * （原版由 MatchServer 服务端队列承担该清理职责）。
 */
@Component
public class GoldRoomMatchSweepScheduler {

    private final GoldRoomMatchService matchService;
    private final GoldRoomProperties properties;
    private final Clock clock;

    // 两个构造器并存时 Spring 不再自动选择，必须标注容器使用的入口。
    @Autowired
    public GoldRoomMatchSweepScheduler(
            GoldRoomMatchService matchService, GoldRoomProperties properties) {
        this(matchService, properties, Clock.systemUTC());
    }

    GoldRoomMatchSweepScheduler(
            GoldRoomMatchService matchService,
            GoldRoomProperties properties,
            Clock clock) {
        this.matchService = matchService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${nanbei.gameplay.gold-room.sweep-interval:30s}",
            initialDelayString = "${nanbei.gameplay.gold-room.sweep-initial-delay:10s}")
    public void sweep() {
        matchService.sweepTimedOutRooms(clock.instant().minus(properties.matchTimeout()));
    }
}
