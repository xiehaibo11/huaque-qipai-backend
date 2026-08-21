package com.nanbei.entertainment.backend.gamehome.api;

import com.nanbei.entertainment.backend.gamehome.application.GameHomeService;
import com.nanbei.entertainment.backend.gamehome.application.GameHomeSnapshot;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class GameHomeController {
    private final GameHomeService gameHomeService;

    public GameHomeController(GameHomeService gameHomeService) {
        this.gameHomeService = gameHomeService;
    }

    @GetMapping
    GameHomeSnapshot home(@AuthenticationPrincipal Jwt jwt) {
        return gameHomeService.load(UUID.fromString(jwt.getSubject()));
    }
}
