package com.nanbei.entertainment.backend.roomtools.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.roomtools.domain.RoomToolMessageEntity;
import com.nanbei.entertainment.backend.roomtools.domain.RoomToolOperationEntity;
import com.nanbei.entertainment.backend.roomtools.domain.RoomToolReservationEntity;
import com.nanbei.entertainment.backend.roomtools.infrastructure.RoomToolMessageRepository;
import com.nanbei.entertainment.backend.roomtools.infrastructure.RoomToolOperationRepository;
import com.nanbei.entertainment.backend.roomtools.infrastructure.RoomToolReservationRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopInventoryItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RoomToolsService {
    private static final int MESSAGE_LIMIT = 50;
    /**
     * 30109 resolves its emoji set through GameFace = 2 (GameSub.lua), which Chat/View.lua feeds to
     * GameExpressionConfiger as the ConfID; GameExpression.lua ConfID 2 ("小短腿") lists 20 icons.
     */
    private static final int EMOJI_COUNT = 20;
    private static final int MAX_VOICE_BYTES = 512 * 1024;
    private static final List<String> QUICK_PHRASES =
            List.of(
                    "快点啊，都等到我花儿都谢了！",
                    "怎么又断线了，网络怎么这么差啊！",
                    "不要走决战到天亮！",
                    "你的牌打的也太好了！",
                    "你是妹妹还是哥哥啊？",
                    "和你合作真是太愉快了！",
                    "大家好很高兴见到各位！",
                    "各位，真是不好意思我得离开一会。",
                    "不要吵了，专心玩游戏吧。");

    private final GameRoomRepository roomRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;
    private final RoomToolReservationRepository reservationRepository;
    private final RoomToolMessageRepository messageRepository;
    private final RoomToolOperationRepository operationRepository;
    private final PlayerWalletRepository walletRepository;
    private final ShopInventoryItemRepository inventoryRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RoomToolsService(
            GameRoomRepository roomRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            RoomToolReservationRepository reservationRepository,
            RoomToolMessageRepository messageRepository,
            RoomToolOperationRepository operationRepository,
            PlayerWalletRepository walletRepository,
            ShopInventoryItemRepository inventoryRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this(
                roomRepository,
                sessionRepository,
                seatRepository,
                reservationRepository,
                messageRepository,
                operationRepository,
                walletRepository,
                inventoryRepository,
                cryptoService,
                objectMapper,
                Clock.systemUTC());
    }

    RoomToolsService(
            GameRoomRepository roomRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            RoomToolReservationRepository reservationRepository,
            RoomToolMessageRepository messageRepository,
            RoomToolOperationRepository operationRepository,
            PlayerWalletRepository walletRepository,
            ShopInventoryItemRepository inventoryRepository,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.messageRepository = messageRepository;
        this.operationRepository = operationRepository;
        this.walletRepository = walletRepository;
        this.inventoryRepository = inventoryRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RoomToolsStateResponse state(UUID userId, String roomNumber) {
        SessionAccess access = requireAccess(userId, roomNumber);
        List<RoomToolReservationView> reservations =
                reservationRepository
                        .findBySessionIdAndUserIdAndActiveTrueOrderByToolType(
                                access.session().getId(), userId)
                        .stream()
                        .map(RoomToolsService::reservationView)
                        .toList();
        List<RoomToolMessageEntity> newest =
                new ArrayList<>(messageRepository.findLatest(access.session().getId(), MESSAGE_LIMIT));
        Collections.reverse(newest);
        Map<UUID, Integer> seatNumbers = seatNumbers(access.session().getId());
        return new RoomToolsStateResponse(
                roomNumber,
                toolDefinitions(userId),
                reservations,
                QUICK_PHRASES,
                EMOJI_COUNT,
                newest.stream().map(message -> messageView(message, seatNumbers)).toList());
    }

    @Transactional
    public RoomToolReservationResponse setReservation(
            UUID userId,
            String roomNumber,
            RoomToolType type,
            String idempotencyKey,
            boolean active) {
        SessionAccess access = requireAccess(userId, roomNumber);
        String safeKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = hash("RESERVATION|" + roomNumber + "|" + type + "|" + active);
        RoomToolReservationResponse replay =
                replay(userId, safeKey, requestHash, RoomToolReservationResponse.class);
        if (replay != null) {
            return replay.asReplay();
        }
        int targetRound = access.session().getRoundNumber() + 1;
        operationRepository.acquireOperationLock(
                "room-tool-reservation:"
                        + access.session().getId()
                        + ":"
                        + userId
                        + ":"
                        + type
                        + ":"
                        + targetRound);
        RoomToolReservationEntity reservation =
                reservationRepository
                        .findBySessionIdAndUserIdAndToolTypeAndTargetRound(
                                access.session().getId(), userId, type, targetRound)
                        .orElseGet(
                                () ->
                                        new RoomToolReservationEntity(
                                                access.session().getId(),
                                                userId,
                                                type,
                                                targetRound,
                                                clock.instant()));
        reservation.setActive(active, clock.instant());
        reservationRepository.save(reservation);
        RoomToolDefinitionView pricing = toolDefinition(userId, type);
        RoomToolReservationResponse response =
                new RoomToolReservationResponse(
                        type,
                        targetRound,
                        active,
                        pricing.priceCurrency(),
                        pricing.priceAmount(),
                        false);
        saveOperation(
                access.session().getId(), userId, safeKey, requestHash, "RESERVATION", response);
        return response;
    }

    @Transactional
    public RoomMessageResponse sendMessage(
            UUID userId,
            String roomNumber,
            String idempotencyKey,
            RoomMessageRequest request) {
        SessionAccess access = requireAccess(userId, roomNumber);
        validateMessage(request);
        String safeKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = hash("MESSAGE|" + roomNumber + "|" + request.type() + "|" + request.contentIndex());
        RoomMessageResponse replay = replay(userId, safeKey, requestHash, RoomMessageResponse.class);
        if (replay != null) {
            return replay.asReplay();
        }
        RoomToolMessageEntity message =
                request.type() == RoomMessageType.QUICK_PHRASE
                        ? RoomToolMessageEntity.quickPhrase(
                                access.session().getId(), userId, request.contentIndex(), clock.instant())
                        : RoomToolMessageEntity.emoji(
                                access.session().getId(), userId, request.contentIndex(), clock.instant());
        messageRepository.save(message);
        RoomMessageResponse response =
                new RoomMessageResponse(messageView(message, seatNumbers(access.session().getId())), false);
        saveOperation(access.session().getId(), userId, safeKey, requestHash, "MESSAGE", response);
        return response;
    }

    @Transactional
    public RoomMessageResponse sendVoice(
            UUID userId,
            String roomNumber,
            String idempotencyKey,
            String mediaType,
            int durationMillis,
            byte[] data) {
        SessionAccess access = requireAccess(userId, roomNumber);
        validateVoice(mediaType, durationMillis, data);
        String safeKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = hashVoice(roomNumber, mediaType, durationMillis, data);
        RoomMessageResponse replay = replay(userId, safeKey, requestHash, RoomMessageResponse.class);
        if (replay != null) {
            return replay.asReplay();
        }
        RoomToolMessageEntity message =
                RoomToolMessageEntity.voice(
                        access.session().getId(),
                        userId,
                        mediaType,
                        durationMillis,
                        data,
                        clock.instant());
        messageRepository.save(message);
        RoomMessageResponse response =
                new RoomMessageResponse(messageView(message, seatNumbers(access.session().getId())), false);
        saveOperation(access.session().getId(), userId, safeKey, requestHash, "VOICE", response);
        return response;
    }

    @Transactional(readOnly = true)
    public RoomVoicePayload voice(UUID userId, String roomNumber, UUID messageId) {
        SessionAccess access = requireAccess(userId, roomNumber);
        RoomToolMessageEntity message =
                messageRepository
                        .findByIdAndSessionId(messageId, access.session().getId())
                        .filter(candidate -> candidate.getMessageType() == RoomMessageType.VOICE)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_TOOLS_MESSAGE_NOT_FOUND,
                                                "语音消息不存在"));
        return new RoomVoicePayload(
                message.getVoiceMediaType(),
                message.getVoiceDurationMillis(),
                message.getVoiceData());
    }

    private SessionAccess requireAccess(UUID userId, String roomNumber) {
        GameRoomEntity room =
                roomRepository
                        .findByRoomNumber(roomNumber)
                        .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
        GameSessionEntity session =
                sessionRepository
                        .findByRoomId(room.getId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GAMEPLAY_SESSION_NOT_FOUND,
                                                "牌局尚未创建"));
        GameSessionSeatEntity seat =
                seatRepository
                        .findByIdSessionIdAndUserId(session.getId(), userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GAMEPLAY_FORBIDDEN,
                                                "无权访问该牌局工具"));
        return new SessionAccess(room, session, seat);
    }

    private Map<UUID, Integer> seatNumbers(UUID sessionId) {
        return seatRepository.findByIdSessionIdOrderByIdSeatNumber(sessionId).stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                GameSessionSeatEntity::getUserId,
                                seat -> seat.getId().getSeatNumber()));
    }

    private RoomMessageView messageView(
            RoomToolMessageEntity message, Map<UUID, Integer> seatNumbers) {
        String text =
                message.getMessageType() == RoomMessageType.QUICK_PHRASE
                        ? QUICK_PHRASES.get(message.getContentIndex())
                        : "";
        return new RoomMessageView(
                message.getId(),
                message.getMessageType(),
                message.getContentIndex(),
                text,
                message.getSenderUserId(),
                seatNumbers.getOrDefault(message.getSenderUserId(), 0),
                message.getVoiceDurationMillis(),
                message.getCreatedAt());
    }

    private static RoomToolReservationView reservationView(
            RoomToolReservationEntity reservation) {
        return new RoomToolReservationView(
                reservation.getToolType(),
                reservation.getTargetRound(),
                reservation.isActive(),
                reservation.getUpdatedAt());
    }


    /**
     * 按原版 {@code getShowType()} 为当前玩家解析两个道具的支付方式。
     *
     * <p>只读余额与背包持有量，不做任何扣减：原版预约阶段同样不扣道具，真正的
     * {@code sendRequestUseProps} 发生在下一局生效时。
     */
    private List<RoomToolDefinitionView> toolDefinitions(UUID userId) {
        return List.of(
                toolDefinition(userId, RoomToolType.CHANGE_CARD),
                toolDefinition(userId, RoomToolType.SHUFFLE));
    }

    private RoomToolDefinitionView toolDefinition(UUID userId, RoomToolType type) {
        long roomCardCenti = 0L;
        long diamonds = 0L;
        PlayerWalletEntity wallet = walletRepository.findById(userId).orElse(null);
        if (wallet != null) {
            roomCardCenti = wallet.getRoomCardCenti();
            diamonds = wallet.getDiamonds();
        }
        // 只读定价，不能用 findLocked 的 PESSIMISTIC_WRITE：state() 是只读事务。
        String ticketItem = RoomToolPricing.ticketItem(type);
        long tickets =
                inventoryRepository.findByUserIdOrderByItemCodeAsc(userId).stream()
                        .filter(item -> ticketItem.equals(item.getItemCode()))
                        .mapToLong(item -> item.getQuantity())
                        .findFirst()
                        .orElse(0L);
        return RoomToolDefinitionView.from(type, tickets, roomCardCenti, diamonds);
    }

    private static void validateMessage(RoomMessageRequest request) {
        if (request == null || request.type() == null || request.type() == RoomMessageType.VOICE) {
            throw new ApiException(ErrorCode.ROOM_TOOLS_MESSAGE_INVALID, "消息类型不受支持");
        }
        int limit = request.type() == RoomMessageType.QUICK_PHRASE ? QUICK_PHRASES.size() : EMOJI_COUNT;
        if (request.contentIndex() < 0 || request.contentIndex() >= limit) {
            throw new ApiException(ErrorCode.ROOM_TOOLS_MESSAGE_INVALID, "消息内容不在服务器目录中");
        }
    }

    private static void validateVoice(String mediaType, int durationMillis, byte[] data) {
        if (!"audio/mp4".equalsIgnoreCase(mediaType)
                || durationMillis < 400
                || durationMillis > 30_000
                || data == null
                || data.length == 0
                || data.length > MAX_VOICE_BYTES) {
            throw new ApiException(ErrorCode.ROOM_TOOLS_VOICE_INVALID, "语音格式、时长或大小不合法");
        }
    }

    private String hashVoice(String roomNumber, String mediaType, int durationMillis, byte[] data) {
        return hash(
                "VOICE|"
                        + roomNumber
                        + "|"
                        + mediaType
                        + "|"
                        + durationMillis
                        + "|"
                        + java.util.Base64.getEncoder().encodeToString(data));
    }

    private String hash(String canonical) {
        return cryptoService.sha256(canonical);
    }

    private <T> T replay(UUID userId, String key, String requestHash, Class<T> resultClass) {
        operationRepository.acquireOperationLock("room-tool:" + userId + ":" + key);
        RoomToolOperationEntity existing =
                operationRepository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    ErrorCode.ROOM_TOOLS_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key 已用于不同的房间工具操作");
        }
        try {
            return objectMapper.readValue(existing.getResult(), resultClass);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read room-tool operation", exception);
        }
    }

    private void saveOperation(
            UUID sessionId,
            UUID userId,
            String key,
            String requestHash,
            String type,
            Object response) {
        try {
            operationRepository.save(
                    new RoomToolOperationEntity(
                            sessionId,
                            userId,
                            key,
                            requestHash,
                            type,
                            objectMapper.writeValueAsString(response),
                            clock.instant()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save room-tool operation", exception);
        }
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不合法");
        }
        return value.trim();
    }

    private record SessionAccess(
            GameRoomEntity room, GameSessionEntity session, GameSessionSeatEntity seat) {}
}
