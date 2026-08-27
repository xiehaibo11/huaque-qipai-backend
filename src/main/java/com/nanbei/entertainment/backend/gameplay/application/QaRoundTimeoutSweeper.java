package com.nanbei.entertainment.backend.gameplay.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时托管代打的心跳（自建，原版由 MatchServer 内部定时器承担）：按秒扫进行中的牌局，
 * 把过宽限期的真人动作交给 {@link QaRoundTimeoutService} 代打/代过。
 */
@Component
public class QaRoundTimeoutSweeper {

    private final QaRoundTimeoutService timeoutService;

    QaRoundTimeoutSweeper(QaRoundTimeoutService timeoutService) {
        this.timeoutService = timeoutService;
    }

    @Scheduled(
            fixedDelayString = "${nanbei.gameplay.round-timeout.sweep-interval:1s}",
            initialDelayString = "${nanbei.gameplay.round-timeout.sweep-initial-delay:5s}")
    public void sweep() {
        timeoutService.sweepAllPlaying();
    }
}
