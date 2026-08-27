package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.room.application.TaizhouMahjongRuleDisplay;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GameplaySessionService {
    private static final long TAIZHOU_MAHJONG_GAME_ID = 30109L;

    private final GameRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;
    private final UserRepository userRepository;
    private final PlayerProfileService profileService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public GameplaySessionService(
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            UserRepository userRepository,
            PlayerProfileService profileService,
            ObjectMapper objectMapper) {
        this(
                roomRepository,
                participantRepository,
                sessionRepository,
                seatRepository,
                userRepository,
                profileService,
                objectMapper,
                Clock.systemUTC());
    }

    GameplaySessionService(
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            UserRepository userRepository,
            PlayerProfileService profileService,
            Clock clock) {
        this(
                roomRepository,
                participantRepository,
                sessionRepository,
                seatRepository,
                userRepository,
                profileService,
                new ObjectMapper(),
                clock);
    }

    GameplaySessionService(
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            UserRepository userRepository,
            PlayerProfileService profileService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public GameplaySnapshot open(UUID userId, String roomNumber) {
        GameRoomEntity room =
                roomRepository
                        .findLockedByRoomNumber(roomNumber)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
        requireSupported(room);
        if (room.getStatus() == RoomStatus.DISSOLVED) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "已解散房间不能进入牌局");
        }

        GameSessionEntity existing = sessionRepository.findByRoomId(room.getId()).orElse(null);
        if (existing != null) {
            return snapshot(existing, room, userId, synchronizeSeats(room, existing));
        }
        if (isGoldRoom(room)) {
            throw new ApiException(ErrorCode.GAMEPLAY_SESSION_NOT_FOUND, "金币场牌局尚未创建");
        }
        requireOwner(userId, room);

        List<UUID> userIds = orderedParticipantIds(room);
        Instant now = clock.instant();
        GameSessionEntity session =
                sessionRepository.save(new GameSessionEntity(room.getId(), room.getGameId(), now));
        List<GameSessionSeatEntity> sessionSeats = new ArrayList<>(userIds.size());
        for (int index = 0; index < userIds.size(); index++) {
            sessionSeats.add(
                    new GameSessionSeatEntity(
                            session.getId(), index + 1, userIds.get(index), now));
        }
        seatRepository.saveAll(sessionSeats);
        return snapshot(session, room, userId, sessionSeats);
    }

    @Transactional
    public GameplaySnapshot get(UUID userId, String roomNumber) {
        GameRoomEntity room =
                roomRepository
                        .findLockedByRoomNumber(roomNumber)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
        requireSupported(room);
        if (room.getStatus() == RoomStatus.DISSOLVED) {
            return dissolvedSnapshot(room);
        }
        GameSessionEntity session =
                sessionRepository
                        .findByRoomId(room.getId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GAMEPLAY_SESSION_NOT_FOUND,
                                                "牌局尚未创建"));
        return snapshot(session, room, userId, synchronizeSeats(room, session));
    }

    /**
     * 已解散房间的快照：客户端匹配轮询靠 phase=DISSOLVED 撤下等待页（对应原版房散通知），
     * 不能抛 ROOM_ILLEGAL_STATE 把轮询打成无限重试；也不再增删座位或校验房主座位，
     * 座位列表置空、mySeat 给范围内占位值。
     */
    private GameplaySnapshot dissolvedSnapshot(GameRoomEntity room) {
        GameSessionEntity session =
                sessionRepository.findByRoomId(room.getId()).orElse(null);
        return new GameplaySnapshot(
                session == null ? null : session.getId(),
                room.getRoomNumber(),
                room.getGameId(),
                com.nanbei.entertainment.backend.gameplay.domain.GamePhase.DISSOLVED,
                session == null ? 0 : session.getRoundNumber(),
                session == null ? 0 : session.getRevision(),
                room.getPlayerCount(),
                room.getPlayCount(),
                room.getGameRuleDisplay(),
                false,
                1,
                List.of(),
                room.getClosedAt() != null ? room.getClosedAt() : room.getCreatedAt());
    }

    private List<GameSessionSeatEntity> synchronizeSeats(
            GameRoomEntity room, GameSessionEntity session) {
        List<UUID> participantIds = orderedParticipantIds(room);
        List<GameSessionSeatEntity> current = new ArrayList<>(seats(session.getId()));
        if (session.getPhase()
                == com.nanbei.entertainment.backend.gameplay.domain.GamePhase.WAITING) {
            List<GameSessionSeatEntity> staleSeats =
                    current.stream()
                            .filter(seat -> !participantIds.contains(seat.getUserId()))
                            .toList();
            if (!staleSeats.isEmpty()) {
                seatRepository.deleteAll(staleSeats);
                seatRepository.flush();
                current.removeAll(staleSeats);
            }
        }
        List<UUID> seatedUserIds = current.stream().map(GameSessionSeatEntity::getUserId).toList();
        List<GameSessionSeatEntity> additions = new ArrayList<>();
        for (UUID participantId : participantIds) {
            if (!seatedUserIds.contains(participantId)) {
                GameSessionSeatEntity addition =
                        new GameSessionSeatEntity(
                                session.getId(),
                                firstVacantSeat(current, room.getPlayerCount()),
                                participantId,
                                clock.instant());
                additions.add(addition);
                current.add(addition);
            }
        }
        if (!additions.isEmpty()) {
            seatRepository.saveAll(additions);
            current.sort(Comparator.comparingInt(seat -> seat.getId().getSeatNumber()));
        }
        return current;
    }

    private static int firstVacantSeat(
            List<GameSessionSeatEntity> seats, int playerCount) {
        for (int seatNumber = 1; seatNumber <= playerCount; seatNumber++) {
            int candidate = seatNumber;
            if (seats.stream().noneMatch(seat -> seat.getId().getSeatNumber() == candidate)) {
                return seatNumber;
            }
        }
        throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "房间座位数据不合法");
    }

    private List<UUID> orderedParticipantIds(GameRoomEntity room) {
        List<UUID> participantIds =
                participantRepository.findByIdRoomIdOrderByIdUserId(room.getId()).stream()
                        .map(RoomParticipantEntity::getUserId)
                        .toList();
        if (participantIds.isEmpty() || participantIds.size() > room.getPlayerCount()) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "房间座位数据不合法");
        }
        if (!participantIds.contains(room.getOwnerUserId())) {
            if (isGoldRoom(room)) {
                return participantIds;
            }
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "房间缺少房主座位");
        }
        List<UUID> ordered = new ArrayList<>(participantIds.size());
        ordered.add(room.getOwnerUserId());
        participantIds.stream()
                .filter(userId -> !userId.equals(room.getOwnerUserId()))
                .sorted(Comparator.naturalOrder())
                .forEach(ordered::add);
        return ordered;
    }

    private List<GameSessionSeatEntity> seats(UUID sessionId) {
        return seatRepository.findByIdSessionIdOrderByIdSeatNumber(sessionId);
    }

    private GameplaySnapshot snapshot(
            GameSessionEntity session,
            GameRoomEntity room,
            UUID userId,
            List<GameSessionSeatEntity> seats) {
        int mySeat =
                seats.stream()
                        .filter(seat -> seat.getUserId().equals(userId))
                        .mapToInt(seat -> seat.getId().getSeatNumber())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.GAMEPLAY_FORBIDDEN,
                                                "无权查看该牌局"));
        List<GameplaySeatSnapshot> seatSnapshots =
                seats.stream()
                        .map(seat -> seatSnapshot(room, seat))
                        .toList();
        String gameRuleDisplay = room.getGameRuleDisplay();
        if (gameRuleDisplay.isBlank()) {
            gameRuleDisplay =
                    TaizhouMahjongRuleDisplay.render(
                            room.getGameRule(),
                            room.getPlayerCount(),
                            room.getPlayCount(),
                            room.getPayType());
        }
        JsonNode state = readSessionState(session);
        return new GameplaySnapshot(
                session.getId(),
                room.getRoomNumber(),
                session.getGameId(),
                room.getRoomMode(),
                room.getVenue() == null ? "" : room.getVenue().name(),
                session.getPhase(),
                session.getRoundNumber(),
                session.getRevision(),
                room.getPlayerCount(),
                room.getPlayCount(),
                gameRuleDisplay,
                TaizhouMahjongRuleDisplay.isAutoReady(room.getGameRule()),
                mySeat,
                seatSnapshots,
                seatScopedStateField(state, "visibleRoundsBySeat", mySeat),
                seatScopedStateField(state, "playPermissionsBySeat", mySeat),
                jsonField(state, "settlement"),
                viewerMultipleChoice(state, mySeat),
                optionalIntegerField(state, "activeSeat"),
                countdownSeconds(state, clock.instant()),
                integerField(state, "remainingWallCount", -1),
                seatScopedStateField(state, "actionOffersBySeat", mySeat),
                jsonField(state, "melds"),
                jsonField(state, "flowers"),
                seatScopedStateField(state, "tingInfosBySeat", mySeat),
                optionalIntegerField(state, "shengPaiCount"),
                optionalIntegerField(state, "leftBankerCount"),
                session.getUpdatedAt());
    }

    private JsonNode readSessionState(GameSessionEntity session) {
        try {
            JsonNode state = objectMapper.readTree(session.getState());
            return state == null || state.isNull() ? objectMapper.createObjectNode() : state;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read gameplay session state", exception);
        }
    }

    private static Integer countdownSeconds(JsonNode state, Instant now) {
        Integer configured = optionalIntegerField(state, "clockRemainingSeconds");
        if (configured == null) {
            return null;
        }
        long offeredAt = Long.MAX_VALUE;
        JsonNode offers = state.path("qaRound").path("offers");
        for (Map.Entry<String, JsonNode> entry : offers.properties()) {
            JsonNode offer = entry.getValue();
            if (!offer.path("passed").asBoolean() && offer.path("offeredAtEpochMilli").isNumber()) {
                offeredAt = Math.min(offeredAt, offer.path("offeredAtEpochMilli").asLong());
            }
        }
        if (offeredAt == Long.MAX_VALUE) {
            return configured;
        }
        long elapsedSeconds = Math.max(0L, now.toEpochMilli() - offeredAt) / 1000L;
        return (int) Math.max(0L, configured - elapsedSeconds);
    }

    private static JsonNode seatScopedStateField(
            JsonNode state, String bySeatField, int mySeat) {
        JsonNode bySeat = jsonField(state, bySeatField);
        if (bySeat == null || !bySeat.isObject()) {
            return null;
        }
        return jsonField(bySeat, Integer.toString(mySeat));
    }

    private static JsonNode viewerMultipleChoice(JsonNode state, int mySeat) {
        JsonNode multipleChoice = jsonField(state, "multipleChoice");
        if (!(multipleChoice instanceof ObjectNode)) {
            return multipleChoice;
        }
        ObjectNode scoped = (ObjectNode) multipleChoice.deepCopy();
        scoped.put("mySeat", mySeat);
        return scoped;
    }

    private static JsonNode jsonField(JsonNode state, String fieldName) {
        if (state == null || state.isNull()) {
            return null;
        }
        JsonNode value = state.get(fieldName);
        return value == null || value.isNull() ? null : value;
    }

    private static Integer optionalIntegerField(JsonNode state, String fieldName) {
        JsonNode value = jsonField(state, fieldName);
        return value != null && value.isNumber() ? value.intValue() : null;
    }

    private static int integerField(JsonNode state, String fieldName, int fallback) {
        Integer value = optionalIntegerField(state, fieldName);
        return value == null ? fallback : value;
    }

    private GameplaySeatSnapshot seatSnapshot(
            GameRoomEntity room, GameSessionSeatEntity seat) {
        UserEntity user =
                userRepository
                        .findById(seat.getUserId())
                        .filter(UserEntity::isActive)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.ROOM_ILLEGAL_STATE,
                                                "房间玩家资料不完整"));
        PlayerProfileEntity profile = profileService.ensureProfile(seat.getUserId());
        return new GameplaySeatSnapshot(
                seat.getId().getSeatNumber(),
                seat.getUserId(),
                profile.getPublicPlayerId(),
                user.getDisplayName(),
                profile.getAvatarKey(),
                seat.getScore(),
                room.getOwnerUserId().equals(seat.getUserId()),
                seat.isReady(),
                seat.isConnected());
    }

    private static void requireOwner(UUID userId, GameRoomEntity room) {
        if (!room.getOwnerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.ROOM_FORBIDDEN, "只有房主可以创建牌局");
        }
    }

    private static void requireSupported(GameRoomEntity room) {
        if (room.getGameId() != TAIZHOU_MAHJONG_GAME_ID) {
            throw new ApiException(
                    ErrorCode.GAMEPLAY_NOT_AVAILABLE,
                    "当前游戏尚未接入服务端牌局引擎");
        }
    }

    private static boolean isGoldRoom(GameRoomEntity room) {
        return room.getVenue() == RoomVenue.GOLD || room.getRoomMode() == 50;
    }
}
