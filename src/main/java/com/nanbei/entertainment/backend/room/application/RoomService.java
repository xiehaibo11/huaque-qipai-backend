package com.nanbei.entertainment.backend.room.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomCardLedgerEntity;
import com.nanbei.entertainment.backend.room.domain.RoomGameId;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomCardLedgerRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomGameRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomRuleConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RoomService {
    private static final int ROOM_NUMBER_ALLOCATION_ATTEMPTS = 20;

    private final GameRoomRepository roomRepository;
    private final RoomGameRepository gameRepository;
    private final RoomRuleConfigRepository configRepository;
    private final RoomCardLedgerRepository ledgerRepository;
    private final RoomParticipantRepository participantRepository;
    private final PlayerWalletRepository walletRepository;
    private final RoomRuleAssembler ruleAssembler;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final RoomPlacementService placementService;

    public RoomService(
            GameRoomRepository roomRepository,
            RoomGameRepository gameRepository,
            RoomRuleConfigRepository configRepository,
            RoomCardLedgerRepository ledgerRepository,
            RoomParticipantRepository participantRepository,
            PlayerWalletRepository walletRepository,
            RoomRuleAssembler ruleAssembler,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            RoomPlacementService placementService) {
        this.roomRepository = roomRepository;
        this.gameRepository = gameRepository;
        this.configRepository = configRepository;
        this.ledgerRepository = ledgerRepository;
        this.participantRepository = participantRepository;
        this.walletRepository = walletRepository;
        this.ruleAssembler = ruleAssembler;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.placementService = placementService;
    }

    @Transactional
    public RoomSnapshot create(
            UUID ownerUserId, RoomCreateCommand command, String idempotencyKey) {
        String safeKey = requireIdempotencyKey(idempotencyKey);
        placementService.lockUser(ownerUserId);
        String requestHash = cryptoService.sha256(command.canonicalValue());
        var existing =
                roomRepository.findByOwnerUserIdAndCreationIdempotencyKey(
                        ownerUserId, safeKey);
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().getCreationRequestHash())) {
                throw new ApiException(
                        ErrorCode.ROOM_IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key 已用于不同的创建房间请求");
            }
            return RoomSnapshot.from(existing.get());
        }
        placementService.requireNoOtherActiveBoxRoom(ownerUserId, null);

        RoomGameId gameId = new RoomGameId(command.lobbyId(), command.gameId());
        var game =
                gameRepository
                        .findById(gameId)
                        .filter(candidate -> candidate.isEnabled())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_GAME_NOT_FOUND,
                                                "房间游戏不存在或不可创建"));
        var storedConfig =
                configRepository
                        .findById(game.getId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_GAME_NOT_FOUND,
                                                "房间规则配置不存在"));
        RoomRuleSelection selection;
        try {
            JsonNode config = objectMapper.readTree(storedConfig.getConfig());
            selection =
                    ruleAssembler.assemble(
                            config,
                            command.categoryIndex(),
                            command.selectedNodeNames());
        } catch (RoomRuleValidationException exception) {
            throw new ApiException(ErrorCode.ROOM_RULE_INVALID, exception.getMessage());
        } catch (Exception exception) {
            throw new IllegalStateException("room rule config is invalid", exception);
        }

        var wallet =
                walletRepository
                        .findLockedByUserId(ownerUserId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_INSUFFICIENT_BALANCE,
                                                "房卡余额不足"));
        if (wallet.getRoomCardCenti() < selection.roomFeeCenti()) {
            throw new ApiException(ErrorCode.ROOM_INSUFFICIENT_BALANCE, "房卡余额不足");
        }

        String gameRuleDisplay =
                command.gameId() == 30109L
                        ? TaizhouMahjongRuleDisplay.render(
                                selection.gameRule(),
                                selection.playerCount(),
                                selection.playCount(),
                                selection.payType())
                        : selection.gameRuleDisplay();
        GameRoomEntity room =
                new GameRoomEntity(
                        allocateRoomNumber(),
                        ownerUserId,
                        command.lobbyId(),
                        command.gameId(),
                        selection.gameRule(),
                        gameRuleDisplay,
                        OriginalRoomRuleAssembler.assemble(command.gameId(), selection),
                        selection.roomMode(),
                        selection.playerCount(),
                        selection.playCount(),
                        selection.payType(),
                        selection.roomFeeCenti(),
                        safeKey,
                        requestHash);
        roomRepository.saveAndFlush(room);
        participantRepository.save(new RoomParticipantEntity(room.getId(), ownerUserId));
        return RoomSnapshot.from(room);
    }

    @Transactional(readOnly = true)
    public RoomSnapshot get(UUID userId, String roomNumber) {
        GameRoomEntity room = requireRoom(roomNumber);
        placementService.requireBoxRoom(room);
        requireParticipant(userId, room);
        return RoomSnapshot.from(room);
    }

    @Transactional
    public RoomSnapshot join(UUID userId, String roomNumber) {
        placementService.lockUser(userId);
        GameRoomEntity room = requireLockedRoom(roomNumber);
        placementService.requireBoxRoom(room);
        RoomParticipantId participantId = new RoomParticipantId(room.getId(), userId);
        if (participantRepository.existsById(participantId)) {
            return RoomSnapshot.from(room);
        }
        placementService.requireNoOtherActiveBoxRoom(userId, room.getId());
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "已开局或已解散房间不能加入");
        }
        if (participantRepository.countByIdRoomId(room.getId()) >= room.getPlayerCount()) {
            throw new ApiException(ErrorCode.ROOM_FULL, "房间人数已满");
        }
        if (room.getPayType() == RoomPayType.AA) {
            requireSufficientWallet(userId, room.getRoomFeeCenti());
        }
        participantRepository.save(new RoomParticipantEntity(room.getId(), userId));
        return RoomSnapshot.from(room);
    }

    @Transactional
    public RoomSnapshot firstRound(UUID userId, String roomNumber) {
        GameRoomEntity room = requireLockedRoom(roomNumber);
        placementService.requireBoxRoom(room);
        requireOwner(userId, room);
        if (room.getStatus() == RoomStatus.CHARGED) {
            return RoomSnapshot.from(room);
        }
        if (room.getStatus() == RoomStatus.DISSOLVED) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "已解散房间不能开局");
        }
        List<RoomParticipantEntity> participants =
                participantRepository.findByIdRoomIdOrderByIdUserId(room.getId());
        if (participants.size() != room.getPlayerCount()) {
            throw new ApiException(ErrorCode.ROOM_NOT_FULL, "房间未满员，不能开局");
        }
        List<UUID> payerIds =
                room.getPayType() == RoomPayType.ALL
                        ? List.of(room.getOwnerUserId())
                        : participants.stream().map(RoomParticipantEntity::getUserId).sorted().toList();
        var wallets =
                payerIds.stream()
                        .map(payerId -> requireSufficientWallet(payerId, room.getRoomFeeCenti()))
                        .toList();
        for (int index = 0; index < payerIds.size(); index++) {
            var wallet = wallets.get(index);
            if (room.getRoomFeeCenti() > 0) {
                wallet.debitRoomCardCenti(room.getRoomFeeCenti());
            }
            ledgerRepository.save(
                    new RoomCardLedgerEntity(
                            payerIds.get(index), room.getId(), -room.getRoomFeeCenti()));
        }
        room.markFirstRound(Instant.now());
        return RoomSnapshot.from(room);
    }

    @Transactional
    public RoomSnapshot dissolve(UUID userId, String roomNumber) {
        GameRoomEntity room = requireLockedRoom(roomNumber);
        placementService.requireBoxRoom(room);
        requireOwner(userId, room);
        if (room.getStatus() == RoomStatus.DISSOLVED) {
            return RoomSnapshot.from(room);
        }
        room.dissolve(Instant.now());
        return RoomSnapshot.from(room);
    }

    private GameRoomEntity requireRoom(String roomNumber) {
        return roomRepository
                .findByRoomNumber(roomNumber)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
    }

    private GameRoomEntity requireLockedRoom(String roomNumber) {
        return roomRepository
                .findLockedByRoomNumber(roomNumber)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
    }

    private static void requireOwner(UUID userId, GameRoomEntity room) {
        if (!room.getOwnerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.ROOM_FORBIDDEN, "无权操作该房间");
        }
    }

    private void requireParticipant(UUID userId, GameRoomEntity room) {
        if (!participantRepository.existsById(new RoomParticipantId(room.getId(), userId))) {
            throw new ApiException(ErrorCode.ROOM_FORBIDDEN, "无权查看该房间");
        }
    }

    private PlayerWalletEntity requireSufficientWallet(UUID userId, int amountCenti) {
        var wallet =
                walletRepository
                        .findLockedByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_INSUFFICIENT_BALANCE,
                                                "房卡余额不足"));
        if (wallet.getRoomCardCenti() < amountCenti) {
            throw new ApiException(ErrorCode.ROOM_INSUFFICIENT_BALANCE, "房卡余额不足");
        }
        return wallet;
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

    private String allocateRoomNumber() {
        for (int attempt = 0; attempt < ROOM_NUMBER_ALLOCATION_ATTEMPTS; attempt++) {
            String candidate = roomRepository.nextRoomNumber();
            if (!roomRepository.existsByRoomNumber(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.ROOM_NUMBER_EXHAUSTED, "暂时无法分配房间号");
    }
}
