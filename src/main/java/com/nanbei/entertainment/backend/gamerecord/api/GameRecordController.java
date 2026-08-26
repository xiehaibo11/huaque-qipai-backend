package com.nanbei.entertainment.backend.gamerecord.api;

import com.nanbei.entertainment.backend.gamerecord.application.GameRecordMode;
import com.nanbei.entertainment.backend.gamerecord.application.GameRecordPage;
import com.nanbei.entertainment.backend.gamerecord.application.GameRecordService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game-records")
public class GameRecordController {
    private final GameRecordService service;

    public GameRecordController(GameRecordService service) {
        this.service = service;
    }

    @GetMapping
    GameRecordPage page(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") long gameId,
            @RequestParam(defaultValue = "BATTLE") GameRecordMode mode) {
        return service.page(UUID.fromString(jwt.getSubject()), date, gameId, mode);
    }
}
