package com.nanbei.entertainment.backend.goldroom.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameEntity;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameId;
import com.nanbei.entertainment.backend.goldroom.infrastructure.GoldGameLevelRepository;
import com.nanbei.entertainment.backend.goldroom.infrastructure.GoldGameRepository;
import com.nanbei.entertainment.backend.gameplay.application.GoldRoomMatchService;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the gold-room (休闲场) catalog that backs the lobby grid and the choose-room page. */
@Service
public class GoldRoomCatalogService {
    private final GoldGameRepository gameRepository;
    private final GoldGameLevelRepository levelRepository;
    private final GameRoomRepository roomRepository;

    public GoldRoomCatalogService(
            GoldGameRepository gameRepository,
            GoldGameLevelRepository levelRepository,
            GameRoomRepository roomRepository) {
        this.gameRepository = gameRepository;
        this.levelRepository = levelRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<GoldGameView> games(long lobbyId) {
        return gameRepository.findByIdLobbyIdAndEnabledTrueOrderBySortOrder(lobbyId).stream()
                .map(GoldGameView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoldRoomConfView conf(long lobbyId, long gameId) {
        GoldGameEntity game =
                gameRepository
                        .findById(new GoldGameId(lobbyId, gameId))
                        .filter(GoldGameEntity::isEnabled)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GOLD_GAME_NOT_FOUND,
                                                "金币场游戏不存在"));
        List<GoldLevelView> levels =
                levelRepository
                        .findByIdLobbyIdAndIdGameIdAndEnabledTrueOrderBySortOrder(lobbyId, gameId)
                        .stream()
                        .map(
                                level ->
                                        GoldLevelView.from(
                                                level,
                                                roomRepository.countActiveGoldPlayers(
                                                        game.getBoxGameId(),
                                                        50,
                                                        GoldRoomMatchService.matchKey(
                                                                lobbyId,
                                                                game.getBoxGameId(),
                                                                level.getId().getRoomNameFlag()),
                                                        RoomStatus.DISSOLVED)))
                        .toList();
        if (levels.isEmpty()) {
            // 原版 joinGoldRoomFirst 在 roomLevelInfos 为空时弹「获取房间信息出错」并中止，
            // 服务端这里直接拒绝，避免客户端拿到一个无法进入的空选场页。
            throw new ApiException(ErrorCode.GOLD_GAME_NOT_FOUND, "金币场游戏不存在");
        }
        List<Integer> roomFlags = levels.stream().map(GoldLevelView::roomNameFlag).toList();
        return new GoldRoomConfView(GoldGameView.from(game), roomFlags, levels, true);
    }
}
