package com.nanbei.entertainment.backend.gamerule.api;

import com.nanbei.entertainment.backend.gamerule.application.GameRuleDocumentService;
import com.nanbei.entertainment.backend.gamerule.domain.GameRuleDocument;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game-rules")
public class GameRuleController {
    private final GameRuleDocumentService service;

    public GameRuleController(GameRuleDocumentService service) {
        this.service = service;
    }

    @GetMapping("/{gameId}")
    GameRuleDocument document(@PathVariable @Positive long gameId) {
        return service.document(gameId);
    }
}
