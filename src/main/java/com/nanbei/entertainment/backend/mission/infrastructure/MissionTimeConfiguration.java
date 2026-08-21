package com.nanbei.entertainment.backend.mission.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MissionTimeConfiguration {
    @Bean
    Clock missionClock() {
        return Clock.systemUTC();
    }
}
