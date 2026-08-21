package com.nanbei.entertainment.backend.room.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.room.domain.RoomGameId;
import com.nanbei.entertainment.backend.room.infrastructure.RoomGameRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomRuleConfigRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RoomCatalogService {
    private final RoomGameRepository gameRepository;
    private final RoomRuleConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public RoomCatalogService(
            RoomGameRepository gameRepository,
            RoomRuleConfigRepository configRepository,
            ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RoomGameView> games(long lobbyId) {
        return gameRepository.findByIdLobbyIdAndEnabledTrueOrderBySortOrder(lobbyId)
                .stream()
                .map(RoomGameView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JsonNode ruleConfig(long lobbyId, long gameId) {
        RoomGameId id = new RoomGameId(lobbyId, gameId);
        gameRepository
                .findById(id)
                .filter(game -> game.isEnabled())
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.ROOM_GAME_NOT_FOUND,
                                        "房间游戏或规则配置不存在"));
        var config =
                configRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_GAME_NOT_FOUND,
                                                "房间游戏或规则配置不存在"));
        try {
            return objectMapper.readTree(config.getConfig());
        } catch (Exception exception) {
            throw new IllegalStateException("room rule config is invalid", exception);
        }
    }
}
