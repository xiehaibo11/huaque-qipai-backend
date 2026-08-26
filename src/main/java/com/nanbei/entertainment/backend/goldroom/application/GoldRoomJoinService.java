package com.nanbei.entertainment.backend.goldroom.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.gameplay.application.QaGoldRoomAutoMatchResult;
import com.nanbei.entertainment.backend.gameplay.application.QaGoldRoomAutoMatchService;
import com.nanbei.entertainment.backend.gameplay.application.GoldRoomMatchResult;
import com.nanbei.entertainment.backend.gameplay.application.GoldRoomMatchService;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameEntity;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameId;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameLevelEntity;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameLevelId;
import com.nanbei.entertainment.backend.goldroom.domain.GoldRoomJoinOperationEntity;
import com.nanbei.entertainment.backend.goldroom.infrastructure.GoldGameLevelRepository;
import com.nanbei.entertainment.backend.goldroom.infrastructure.GoldGameRepository;
import com.nanbei.entertainment.backend.goldroom.infrastructure.GoldRoomJoinOperationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Validates an original gold-room level and enters its real-player match queue. */
@Service
public class GoldRoomJoinService {
    static final String LOW_LIMIT_MESSAGE = "金币不足！补充金币，再战四方！";
    static final String HIGH_LIMIT_MESSAGE = "金币满载，请前往更高级房间，体验更丰富的游戏乐趣!";

    private final GoldGameRepository gameRepository;
    private final GoldGameLevelRepository levelRepository;
    private final GoldRoomJoinOperationRepository operationRepository;
    private final PlayerWalletRepository walletRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final QaGoldRoomAutoMatchService qaAutoMatchService;
    private final GoldRoomMatchService matchService;
    private final Clock clock;

    @Autowired
    public GoldRoomJoinService(
            GoldGameRepository gameRepository,
            GoldGameLevelRepository levelRepository,
            GoldRoomJoinOperationRepository operationRepository,
            PlayerWalletRepository walletRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            QaGoldRoomAutoMatchService qaAutoMatchService,
            GoldRoomMatchService matchService) {
        this(
                gameRepository,
                levelRepository,
                operationRepository,
                walletRepository,
                cryptoService,
                objectMapper,
                qaAutoMatchService,
                matchService,
                Clock.systemUTC());
    }

    GoldRoomJoinService(
            GoldGameRepository gameRepository,
            GoldGameLevelRepository levelRepository,
            GoldRoomJoinOperationRepository operationRepository,
            PlayerWalletRepository walletRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            QaGoldRoomAutoMatchService qaAutoMatchService,
            GoldRoomMatchService matchService,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.levelRepository = levelRepository;
        this.operationRepository = operationRepository;
        this.walletRepository = walletRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.qaAutoMatchService = qaAutoMatchService;
        this.matchService = matchService;
        this.clock = clock;
    }

    @Transactional
    public GoldRoomJoinResponse join(
            UUID userId, long gameId, GoldRoomJoinRequest request, String idempotencyKey) {
        String safeKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = cryptoService.sha256(request.canonicalValue(gameId));
        operationRepository.acquireJoinLock("gold-room-join:" + userId + ":" + safeKey);
        GoldRoomJoinOperationEntity existing =
                operationRepository
                        .findByUserIdAndIdempotencyKey(userId, safeKey)
                        .orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }

        GoldGameEntity game = requireGame(request.lobbyId(), gameId);
        GoldGameLevelEntity level =
                levelRepository
                        .findById(new GoldGameLevelId(request.lobbyId(), gameId, request.roomNameFlag()))
                        .filter(GoldGameLevelEntity::isEnabled)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GOLD_GAME_NOT_FOUND,
                                                "金币场游戏不存在"));
        PlayerWalletEntity wallet =
                walletRepository
                        .findLockedByUserId(userId)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.GOLD_LOW_LIMIT, LOW_LIMIT_MESSAGE));
        validateRichLimit(wallet.getCoins(), level);

        GoldRoomJoinResponse response = response(game, level, userId, wallet.getCoins(), safeKey);
        operationRepository.save(
                new GoldRoomJoinOperationEntity(
                        userId,
                        safeKey,
                        requestHash,
                        request.lobbyId(),
                        gameId,
                        request.roomNameFlag(),
                        json(response),
                        clock.instant()));
        return response;
    }

    /**
     * Cancels a pending match (original PlayerLeaveRequest). Idempotent: leaving with no live
     * gold room still succeeds so the client can fire-and-forget on back.
     */
    @Transactional
    public GoldRoomLeaveResponse leave(
            UUID userId, long gameId, GoldRoomLeaveRequest request) {
        GoldGameEntity game = requireGame(request.lobbyId(), gameId);
        long boxGameId = game.getBoxGameId() == null ? 0L : game.getBoxGameId();
        matchService.leave(userId, request.lobbyId(), boxGameId, request.roomNameFlag());
        return GoldRoomLeaveResponse.left();
    }

    private GoldGameEntity requireGame(long lobbyId, long gameId) {
        return gameRepository
                .findById(new GoldGameId(lobbyId, gameId))
                .filter(GoldGameEntity::isEnabled)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.GOLD_GAME_NOT_FOUND, "金币场游戏不存在"));
    }

    private static void validateRichLimit(long coins, GoldGameLevelEntity level) {
        if (coins < level.getMinRich()) {
            throw new ApiException(ErrorCode.GOLD_LOW_LIMIT, LOW_LIMIT_MESSAGE);
        }
        if (level.getMaxRich() != GoldGameLevelEntity.UNBOUNDED_MAX_RICH
                && coins > level.getMaxRich()) {
            throw new ApiException(ErrorCode.GOLD_HIGH_LIMIT, HIGH_LIMIT_MESSAGE);
        }
    }

    private GoldRoomJoinResponse response(
            GoldGameEntity game,
            GoldGameLevelEntity level,
            UUID userId,
            long initialCoins,
            String idempotencyKey) {
        long boxGameId = game.getBoxGameId() == null ? 0L : game.getBoxGameId();
        int flag = level.getId().getRoomNameFlag();
        QaGoldRoomAutoMatchResult qaMatch =
                qaAutoMatchService == null
                        ? new QaGoldRoomAutoMatchResult(null, false)
                        : qaAutoMatchService.matchAndAutoPlay(
                                userId,
                                game.getId().getLobbyId(),
                                boxGameId,
                                flag,
                                level.getBaseScore(),
                                level.getMinRich(),
                                initialCoins,
                                idempotencyKey);
        GoldRoomMatchResult match =
                qaMatch.autoGameplay()
                        ? new GoldRoomMatchResult(qaMatch.roomNumber(), true)
                        : matchService.match(
                                userId,
                                game.getId().getLobbyId(),
                                boxGameId,
                                flag,
                                level.getChairCount(),
                                level.getBaseScore(),
                                initialCoins,
                                idempotencyKey);
        boolean ready = match.ready();
        boolean qa = qaMatch.autoGameplay();
        return new GoldRoomJoinResponse(
                qa ? "GOLD_QA_AUTO_ROUND_READY" : ready ? "GOLD_ROUND_READY" : "GOLD_MATCHING",
                ready ? "READY" : "MATCHING",
                qa ? "QA_AUTO_MATCH" : "SERVER_AUTHORITY",
                game.getId().getLobbyId(),
                game.getId().getGameId(),
                boxGameId,
                flag,
                flag,
                level.getChairCount(),
                level.getBaseScore(),
                level.isDynamicCost(),
                level.getMinRich(),
                level.getMaxRich(),
                "gold-match-" + UUID.randomUUID(),
                qa
                        ? "牌友已加入，自动牌局已开始"
                        : ready ? "牌友已加入，牌局已开始" : "正在匹配玩家...",
                match.roomNumber(),
                ready,
                false);
    }

    private GoldRoomJoinResponse replay(
            GoldRoomJoinOperationEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    ErrorCode.GOLD_ROOM_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key 已用于不同的金币场进房请求");
        }
        try {
            return objectMapper
                    .readValue(existing.getResult(), GoldRoomJoinResponse.class)
                    .asReplay();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read gold-room join result", exception);
        }
    }

    private String json(GoldRoomJoinResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save gold-room join result", exception);
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不能为空");
        }
        String safeKey = idempotencyKey.trim();
        if (safeKey.length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 过长");
        }
        return safeKey;
    }
}
