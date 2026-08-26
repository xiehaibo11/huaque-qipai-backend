package com.nanbei.entertainment.backend.gamerule.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamerule.domain.GameRuleDocument;
import com.nanbei.entertainment.backend.gamerule.infrastructure.GameRuleDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameRuleDocumentService {
    private final GameRuleDocumentRepository repository;

    public GameRuleDocumentService(GameRuleDocumentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public GameRuleDocument document(long gameId) {
        return repository
                .findByGameId(gameId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.GAME_RULE_NOT_FOUND,
                                        "游戏规则不存在"));
    }
}
