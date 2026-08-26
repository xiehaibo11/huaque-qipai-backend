package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.application.TaizhouMahjongRuleDisplay;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Transaction-scoped four-real-player matcher for the original gold-room mode 50. */
@Service
public class GoldRoomMatchService {
    private static final int ROOM_MODE = 50;
    private static final int PLAY_COUNT = 1;
    private static final int ROOM_NUMBER_ATTEMPTS = 20;
    private static final String MATCH_HASH_PREFIX = "gold-match";
    private final GameRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;
    private final GameEventRepository eventRepository;
    private final QaGameplayBotService playerDataService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public GoldRoomMatchService(
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameEventRepository eventRepository,
            QaGameplayBotService playerDataService,
            ObjectMapper objectMapper) {
        this(
                roomRepository,
                participantRepository,
                sessionRepository,
                seatRepository,
                eventRepository,
                playerDataService,
                objectMapper,
                Clock.systemUTC());
    }

    GoldRoomMatchService(
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameEventRepository eventRepository,
            QaGameplayBotService playerDataService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.playerDataService = playerDataService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public GoldRoomMatchResult match(
            UUID userId,
            long lobbyId,
            long boxGameId,
            int roomNameFlag,
            int chairCount,
            long baseScore,
            long initialCoins,
            String idempotencyKey) {
        String matchKey = matchKey(lobbyId, boxGameId, roomNameFlag);
        roomRepository.acquireCreationLock(matchKey);
        GameRoomEntity existing = existingRoom(userId, boxGameId, matchKey);
        if (existing != null) {
            return result(existing);
        }
        rejectIfOccupiedElsewhere(userId, matchKey);
        GameRoomEntity room =
                roomRepository.findMatchableGoldRooms(
                                boxGameId, ROOM_MODE, matchKey, RoomStatus.OPEN)
                        .stream()
                        .findFirst()
                        .orElseGet(
                                () -> createRoom(
                                        userId,
                                        lobbyId,
                                        boxGameId,
                                        roomNameFlag,
                                        chairCount,
                                        baseScore,
                                        initialCoins,
                                        idempotencyKey,
                                        matchKey));
        if (!room.getOwnerUserId().equals(userId)) {
            addPlayer(room, userId, initialCoins);
        }
        if (participantRepository.countByIdRoomId(room.getId()) == room.getPlayerCount()) {
            start(room);
        }
        return result(room);
    }

    private GameRoomEntity existingRoom(UUID userId, long gameId, String matchKey) {
        for (GameRoomEntity room :
                roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        userId, gameId, ROOM_MODE, matchKey, RoomStatus.DISSOLVED)) {
            if (acceptsReturningPlayer(room)) {
                return room;
            }
            room.dissolve(clock.instant());
            roomRepository.saveAndFlush(room);
        }
        return null;
    }

    /**
     * Cancels one player's pending match, mirroring the original PlayerLeaveRequest even though
     * the stock Lua client never wired it up: with a four-real-player fill model a stranded
     * placeholder would block the room forever, so the first-party client must call this on back.
     */
    public void leave(UUID userId, long lobbyId, long boxGameId, int roomNameFlag) {
        String matchKey = matchKey(lobbyId, boxGameId, roomNameFlag);
        roomRepository.acquireCreationLock(matchKey);
        for (GameRoomEntity room :
                roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        userId, boxGameId, ROOM_MODE, matchKey, RoomStatus.DISSOLVED)) {
            GameSessionEntity session =
                    sessionRepository.findByRoomId(room.getId()).orElse(null);
            if (session != null && session.getRevision() > 0) {
                throw new ApiException(
                        ErrorCode.GOLD_GAMING, "牌局已开始，请回到牌局继续游戏");
            }
            removePlayer(room, session, userId);
        }
    }

    private void removePlayer(
            GameRoomEntity room, GameSessionEntity session, UUID userId) {
        participantRepository.deleteById(new RoomParticipantId(room.getId(), userId));
        if (session != null) {
            seatRepository
                    .findByIdSessionIdAndUserId(session.getId(), userId)
                    .ifPresent(seatRepository::delete);
        }
        List<RoomParticipantEntity> remaining =
                participantRepository.findByIdRoomIdOrderByIdUserId(room.getId());
        if (remaining.isEmpty()) {
            room.dissolve(clock.instant());
            roomRepository.saveAndFlush(room);
            return;
        }
        if (room.getOwnerUserId().equals(userId)) {
            room.transferOwnership(successorOwner(session, remaining));
            roomRepository.saveAndFlush(room);
        }
    }

    /**
     * 房主离场后的继任者：优先取留下的最小座位号（座位顺序即桌位顺序），没有牌局座位
     * 数据时退化为剩余参与者中 userId 最小者，保证房主座位始终在房。
     */
    private UUID successorOwner(
            GameSessionEntity session, List<RoomParticipantEntity> remaining) {
        if (session != null) {
            List<GameSessionSeatEntity> seats = seats(session);
            if (!seats.isEmpty()) {
                return seats.get(0).getUserId();
            }
        }
        return remaining.get(0).getUserId();
    }

    /**
     * Mirrors the original MatchServer GOLD_QUEUING / GOLD_OTHERS_GAMING gate (错误码表
     * roommatch_define.lua 13005/13008): joining another gold queue or table while a live
     * gold room already holds the player is rejected. Re-entering the same level is a
     * returning-player case handled by {@link #existingRoom} above, and QA test rooms
     * (QaGoldMatch marker) never block the production queue.
     */
    private void rejectIfOccupiedElsewhere(UUID userId, String matchKey) {
        for (GameRoomEntity room :
                roomRepository.findActiveRoomsForParticipant(
                        userId, RoomVenue.GOLD, RoomStatus.DISSOLVED)) {
            String hash = room.getCreationRequestHash();
            if (hash == null || hash.equals(matchKey)
                    || room.getGameRule().contains("QaGoldMatch='1'")) {
                continue;
            }
            boolean gaming =
                    sessionRepository.findByRoomId(room.getId())
                            .map(session -> session.getRevision() > 0)
                            .orElse(false);
            if (gaming) {
                throw new ApiException(
                        ErrorCode.GOLD_OTHERS_GAMING, "您正在参与其他场次游戏");
            }
            throw new ApiException(ErrorCode.GOLD_QUEUING, "加入失败，玩家仍在队列中");
        }
    }

    private boolean acceptsReturningPlayer(GameRoomEntity room) {
        return sessionRepository.findByRoomId(room.getId())
                .map(GameSessionEntity::getPhase)
                .map(phase ->
                        phase != GamePhase.ROUND_RESULT
                                && phase != GamePhase.COMPLETED
                                && phase != GamePhase.DISSOLVED)
                .orElse(true);
    }

    private GameRoomEntity createRoom(
            UUID userId,
            long lobbyId,
            long boxGameId,
            int roomNameFlag,
            int chairCount,
            long baseScore,
            long initialCoins,
            String idempotencyKey,
            String matchKey) {
        int baoRule = chairCount == 2 ? 0 : 1;
        String gameRule =
                "winLostType='1';playerCount_"
                        + chairCount
                        + ";maxQuanShu='2';liaoDaZiBaoPai='"
                        + baoRule
                        + "';buSiBao='"
                        + baoRule
                        + "';FengDing='0';PayType='0';autoReady='1';forceGPS='1';"
                        + "IsSysTrust='0';GoldMatch='1';basescore='"
                        + baseScore
                        + "';";
        String display =
                TaizhouMahjongRuleDisplay.render(
                        gameRule, chairCount, PLAY_COUNT, RoomPayType.ALL);
        GameRoomEntity room =
                new GameRoomEntity(
                        allocateRoomNumber(),
                        userId,
                        lobbyId,
                        boxGameId,
                        gameRule,
                        display,
                        "roomrule={GamePlayerCount=\"" + chairCount
                                + "\",group=\"" + boxGameId
                                + "\",roomnameflag=\"" + roomNameFlag
                                + "\",roommode=\"50\"}",
                        ROOM_MODE,
                        chairCount,
                        PLAY_COUNT,
                        RoomPayType.ALL,
                        0,
                        "gold-match-" + idempotencyKey,
                        matchKey,
                        RoomVenue.GOLD);
        roomRepository.saveAndFlush(room);
        participantRepository.save(new RoomParticipantEntity(room.getId(), userId));
        Instant now = clock.instant();
        GameSessionEntity session =
                sessionRepository.save(new GameSessionEntity(room.getId(), boxGameId, now));
        seatRepository.save(readySeat(session.getId(), 1, userId, initialCoins, now));
        return room;
    }

    private void addPlayer(GameRoomEntity room, UUID userId, long initialCoins) {
        participantRepository.save(new RoomParticipantEntity(room.getId(), userId));
        GameSessionEntity session =
                sessionRepository.findByRoomId(room.getId()).orElseThrow(
                        () -> new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "金币场牌局不存在"));
        List<GameSessionSeatEntity> seats = seats(session);
        seatRepository.save(
                readySeat(session.getId(), seats.size() + 1, userId, initialCoins, clock.instant()));
    }

    private void start(GameRoomEntity room) {
        GameSessionEntity session =
                sessionRepository.findByRoomId(room.getId()).orElseThrow(
                        () -> new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "金币场牌局不存在"));
        if (session.getRevision() > 0) {
            return;
        }
        Instant now = clock.instant();
        List<GameSessionSeatEntity> seats = seats(session);
        QaRoundCoordinator.QaRoundCommandOutcome outcome =
                new QaRoundCoordinator(playerDataService, objectMapper)
                        .startServerAuthoritativeRound(
                                room, session, room.getOwnerUserId(), seats, 0L, now);
        session.advance(
                outcome.phase(),
                outcome.roundNumber(),
                outcome.revision(),
                json(outcome.state()),
                now);
        saveEvents(session.getId(), outcome.events(), now);
        room.markFirstRound(now);
    }

    private List<GameSessionSeatEntity> seats(GameSessionEntity session) {
        return seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId());
    }

    private static GameSessionSeatEntity readySeat(
            UUID sessionId, int seatNumber, UUID userId, long initialCoins, Instant now) {
        GameSessionSeatEntity seat =
                new GameSessionSeatEntity(sessionId, seatNumber, userId, initialCoins, now);
        seat.setConnected(true, now);
        seat.setReady(true, now);
        return seat;
    }

    private GoldRoomMatchResult result(GameRoomEntity room) {
        boolean ready =
                sessionRepository.findByRoomId(room.getId())
                        .map(session -> session.getRevision() > 0)
                        .orElse(false);
        return new GoldRoomMatchResult(room.getRoomNumber(), ready);
    }

    private void saveEvents(UUID sessionId, List<GameEvent> events, Instant now) {
        for (int index = 0; index < events.size(); index++) {
            GameEvent event = events.get(index);
            GameEventEntity entity =
                    event.audience() == GameEvent.Audience.SEAT
                            ? GameEventEntity.seatEvent(
                                    sessionId, event.revision(), index + 1, event.type(),
                                    event.targetSeat(), json(event.payload()), now)
                            : GameEventEntity.publicEvent(
                                    sessionId, event.revision(), index + 1, event.type(),
                                    json(event.payload()), now);
            eventRepository.save(entity);
        }
    }

    private String allocateRoomNumber() {
        for (int attempt = 0; attempt < ROOM_NUMBER_ATTEMPTS; attempt++) {
            String candidate = roomRepository.nextRoomNumber();
            if (!roomRepository.existsByRoomNumber(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.ROOM_NUMBER_EXHAUSTED, "暂时无法分配金币场房间号");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize gold-room match", exception);
        }
    }

    public static String matchKey(long lobbyId, long gameId, int roomNameFlag) {
        return MATCH_HASH_PREFIX + ":" + lobbyId + ":" + gameId + ":" + roomNameFlag;
    }

    /**
     * 兑底解散超时未凑满的金币匹配房（原版由 MatchServer 服务端队列承担超时清理）：残留
     * 占位若不清理会永久堵住该场次匹配。sweep 与 join/leave 用同一把 matchKey 事务锁
     * 竞争，锁内重读房态与人数后再解散，不误伤刚满员开局的房。
     *
     * @return 本次实际解散的房数
     */
    @Transactional
    public int sweepTimedOutRooms(Instant cutoff) {
        int dissolved = 0;
        for (GameRoomEntity room :
                roomRepository.findTimedOutGoldMatchingRooms(
                        RoomStatus.OPEN, MATCH_HASH_PREFIX + "%", cutoff)) {
            roomRepository.acquireCreationLock(room.getCreationRequestHash());
            Optional<GameRoomEntity> fresh =
                    roomRepository.findByRoomNumber(room.getRoomNumber()).stream()
                            .filter(current -> current.getStatus() == RoomStatus.OPEN)
                            .filter(current ->
                                    participantRepository.countByIdRoomId(current.getId())
                                            < current.getPlayerCount())
                            .findFirst();
            if (fresh.isPresent()) {
                fresh.get().dissolve(clock.instant());
                roomRepository.saveAndFlush(fresh.get());
                dissolved++;
            }
        }
        return dissolved;
    }
}
