package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.config.GameplayQaProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.application.TaizhouMahjongRuleDisplay;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Creates a local/test gold-room match and immediately runs the QA auto-round. */
@Service
public class QaGoldRoomAutoMatchService {
    private static final int ROOM_NUMBER_ALLOCATION_ATTEMPTS = 20;
    private static final long TAIZHOU_BOX_GAME_ID = 30109L;
    private static final int QA_GOLD_ROOM_MODE = 50;
    private static final int QA_PLAY_COUNT = 8;
    private static final String QA_CREATION_REQUEST_HASH = "qa-gold-match";
    private static final String QA_GAME_RULE_PREFIX =
            "winLostType='1';"
                    + "playerCount_4;"
                    + "maxQuanShu='2';"
                    + "liaoDaZiBaoPai='1';"
                    + "buSiBao='1';"
                    + "FengDing='0';"
                    + "PayType='0';"
                    + "autoReady='1';"
                    + "forceGPS='1';"
                    + "IsSysTrust='0';"
                    + "QaGoldMatch='1';";

    private final GameplayQaProperties properties;
    private final GameRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;
    private final GameEventRepository eventRepository;
    private final QaGameplayBotService qaBotService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public QaGoldRoomAutoMatchService(
            GameplayQaProperties properties,
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameEventRepository eventRepository,
            QaGameplayBotService qaBotService,
            ObjectMapper objectMapper) {
        this(
                properties,
                roomRepository,
                participantRepository,
                sessionRepository,
                seatRepository,
                eventRepository,
                qaBotService,
                objectMapper,
                Clock.systemUTC());
    }

    QaGoldRoomAutoMatchService(
            GameplayQaProperties properties,
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameEventRepository eventRepository,
            QaGameplayBotService qaBotService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.properties = properties;
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.qaBotService = qaBotService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public QaGoldRoomAutoMatchResult matchAndAutoPlay(
            UUID userId,
            long lobbyId,
            long boxGameId,
            int roomNameFlag,
            long baseScore,
            long minRich,
            long initialCoins,
            String idempotencyKey) {
        if (!enabled() || boxGameId != TAIZHOU_BOX_GAME_ID) {
            return new QaGoldRoomAutoMatchResult(null, false);
        }
        roomRepository.acquireCreationLock("gold-qa-match:" + userId);
        Instant now = clock.instant();
        GameRoomEntity existingRoom = existingQaRoom(userId, roomNameFlag);
        if (existingRoom != null) {
            GameSessionEntity existingSession =
                    sessionRepository.findByRoomId(existingRoom.getId()).orElse(null);
            if (existingSession != null && hasQaRoundState(existingSession)) {
                return new QaGoldRoomAutoMatchResult(existingRoom.getRoomNumber(), true);
            }
            existingRoom.dissolve(clock.instant());
            roomRepository.saveAndFlush(existingRoom);
        }
        List<UserEntity> botUsers = qaBotService.ensureBotPool(now);
        UUID testOwnerUserId = selectTestOwner(botUsers);
        GameRoomEntity room =
                createRoom(
                        userId,
                        testOwnerUserId,
                        lobbyId,
                        roomNameFlag,
                        baseScore,
                        minRich,
                        idempotencyKey);
        GameSessionEntity session = sessionRepository.save(new GameSessionEntity(room.getId(), boxGameId, now));
        GameSessionSeatEntity ownerSeat =
                new GameSessionSeatEntity(session.getId(), 1, userId, initialCoins, now);
        ownerSeat.setConnected(true, now);
        ownerSeat.setReady(true, now);
        seatRepository.save(ownerSeat);
        List<GameSessionSeatEntity> seats =
                qaBotService.ensureTenBotsAndFillSeats(
                        room, session, List.of(ownerSeat), now, testOwnerUserId, minRich);
        QaTaizhouRoundResult result =
                new QaTaizhouRoundEngine(objectMapper)
                        .start(
                                new QaTaizhouRoundEngine.Request(
                                        boxGameId,
                                        room.getRoomNumber(),
                                        room.getPlayerCount(),
                                        room.getPlayCount(),
                                        room.getGameRuleDisplay(),
                                        session.getRevision(),
                                        session.getRoundNumber(),
                                        qaBotService.seatInputs(room, seats),
                                        now,
                                        true));
        for (GameSessionSeatEntity seat : seats) {
            Long delta = result.scoreDeltasBySeat().get(seat.getId().getSeatNumber());
            if (delta != null && delta != 0L) {
                seat.applyScoreDelta(delta, now);
            }
        }
        session.advance(result.phase(), result.roundNumber(), result.revision(), json(result.state()), now);
        saveEvents(session.getId(), result.events(), now);
        room.markFirstRound(now);
        return new QaGoldRoomAutoMatchResult(room.getRoomNumber(), true);
    }

    private GameRoomEntity createRoom(
            UUID userId,
            UUID testOwnerUserId,
            long lobbyId,
            int roomNameFlag,
            long baseScore,
            long minRich,
            String idempotencyKey) {
        String gameRule =
                QA_GAME_RULE_PREFIX
                        + "basescore='" + baseScore + "';"
                        + "QaBotMinCoins='" + minRich + "';";
        String display =
                TaizhouMahjongRuleDisplay.render(gameRule, 4, QA_PLAY_COUNT, RoomPayType.ALL);
        GameRoomEntity room =
                new GameRoomEntity(
                        allocateRoomNumber(),
                        testOwnerUserId,
                        lobbyId,
                        TAIZHOU_BOX_GAME_ID,
                        gameRule,
                        display,
                        "roomrule={GamePlayerCount=\"4\",group=\"30109\",roomnameflag=\""
                                + roomNameFlag
                                + "\",cancreate=\"1\",roommode=\"50\"}",
                        QA_GOLD_ROOM_MODE,
                        4,
                        QA_PLAY_COUNT,
                        RoomPayType.ALL,
                        0,
                        "gold-qa-match-" + idempotencyKey,
                        qaCreationRequestHash(roomNameFlag),
                        RoomVenue.GOLD);
        roomRepository.saveAndFlush(room);
        participantRepository.save(new RoomParticipantEntity(room.getId(), testOwnerUserId));
        participantRepository.save(new RoomParticipantEntity(room.getId(), userId));
        return room;
    }

    private UUID selectTestOwner(List<UserEntity> botUsers) {
        return botUsers.stream()
                .map(UserEntity::getId)
                .filter(
                        botUserId ->
                                roomRepository
                                        .findByOwnerUserIdAndStatusNot(
                                                botUserId, RoomStatus.DISSOLVED)
                                        .isEmpty())
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.ROOM_ALREADY_OPEN,
                                        "当前测试号房主池已满，请稍后再试"));
    }

    private GameRoomEntity existingQaRoom(UUID userId, int roomNameFlag) {
        return roomRepository.findLiveRoomsForParticipantAndQaMatch(
                        userId,
                        TAIZHOU_BOX_GAME_ID,
                        QA_GOLD_ROOM_MODE,
                        qaCreationRequestHash(roomNameFlag),
                        RoomStatus.DISSOLVED)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static String qaCreationRequestHash(int roomNameFlag) {
        return QA_CREATION_REQUEST_HASH + ":" + roomNameFlag;
    }

    private boolean hasQaRoundState(GameSessionEntity session) {
        try {
            JsonNode state = objectMapper.readTree(session.getState());
            return !state.path("qaRound").isMissingNode()
                    && playPermissionIndexesMatchVisibleHands(state);
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean playPermissionIndexesMatchVisibleHands(JsonNode state) {
        JsonNode permissionsBySeat = state.path("playPermissionsBySeat");
        if (!permissionsBySeat.isObject() || permissionsBySeat.isEmpty()) {
            return true;
        }
        JsonNode visibleRoundsBySeat = state.path("visibleRoundsBySeat");
        if (!visibleRoundsBySeat.isObject()) {
            return false;
        }
        for (Map.Entry<String, JsonNode> entry : permissionsBySeat.properties()) {
            JsonNode visibleRound = visibleRoundsBySeat.path(entry.getKey());
            if (!permissionIndexesMatchVisibleHand(visibleRound, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean permissionIndexesMatchVisibleHand(
            JsonNode visibleRound, JsonNode permission) {
        if (!visibleRound.isObject() || !permission.isObject()) {
            return false;
        }
        JsonNode myHand = visibleHandForSeat(visibleRound, visibleRound.path("mySeat").asInt());
        if (myHand == null || !myHand.isObject()) {
            return false;
        }
        Set<Integer> presentIndexes = new HashSet<>();
        JsonNode drawnTile = myHand.path("drawnTile");
        if (!drawnTile.isMissingNode() && !drawnTile.isNull()) {
            presentIndexes.add(0);
        }
        int concealedCount = myHand.path("concealedTiles").size();
        for (int index = 1; index <= concealedCount; index++) {
            presentIndexes.add(index);
        }
        return permissionArrayMatches(permission.path("playableOriginalIndexes"), presentIndexes)
                && permissionArrayMatches(permission.path("tingOriginalIndexes"), presentIndexes)
                && permissionArrayMatches(permission.path("actionMaskOriginalIndexes"), presentIndexes)
                && permissionArrayMatches(permission.path("preBaoOriginalIndexes"), presentIndexes);
    }

    private static JsonNode visibleHandForSeat(JsonNode visibleRound, int seatNumber) {
        for (JsonNode hand : visibleRound.path("hands")) {
            if (hand.path("seatNumber").asInt() == seatNumber) {
                return hand;
            }
        }
        return null;
    }

    private static boolean permissionArrayMatches(JsonNode indexes, Set<Integer> presentIndexes) {
        if (indexes.isMissingNode() || indexes.isNull()) {
            return true;
        }
        if (!indexes.isArray()) {
            return false;
        }
        for (JsonNode index : indexes) {
            if (!presentIndexes.contains(index.asInt())) {
                return false;
            }
        }
        return true;
    }

    private void saveEvents(UUID sessionId, List<GameEvent> events, Instant occurredAt) {
        for (int index = 0; index < events.size(); index++) {
            GameEvent event = events.get(index);
            eventRepository.save(
                    event.audience() == GameEvent.Audience.SEAT
                            ? GameEventEntity.seatEvent(
                                    sessionId,
                                    event.revision(),
                                    index + 1,
                                    event.type(),
                                    event.targetSeat(),
                                    json(event.payload()),
                                    occurredAt)
                            : GameEventEntity.publicEvent(
                                    sessionId,
                                    event.revision(),
                                    index + 1,
                                    event.type(),
                                    json(event.payload()),
                                    occurredAt));
        }
    }

    private String allocateRoomNumber() {
        for (int attempt = 0; attempt < ROOM_NUMBER_ALLOCATION_ATTEMPTS; attempt++) {
            String candidate = roomRepository.nextRoomNumber();
            if (!roomRepository.existsByRoomNumber(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.ROOM_NUMBER_EXHAUSTED, "暂时无法分配测试房间号");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize QA gold-room auto match", exception);
        }
    }
}
