package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameEvent;
import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameEventRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GameplayEventService {
    private final GameRoomRepository roomRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;
    private final GameEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public GameplayEventService(
            GameRoomRepository roomRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository,
            GameEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<GameplayEventView> after(
            UUID userId, String roomNumber, long afterRevision) {
        if (afterRevision < 0) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "afterRevision 不能小于零");
        }
        GameRoomEntity room =
                roomRepository
                        .findByRoomNumber(roomNumber)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
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
                                                "无权读取该牌局事件"));
        int seatNumber = seat.getId().getSeatNumber();
        List<GameEventEntity> visibleEvents =
                eventRepository
                .findBySessionIdAndRevisionGreaterThanOrderByRevisionAscEventOrderAsc(
                        session.getId(), afterRevision)
                .stream()
                .filter(event -> visibleTo(event, seatNumber))
                .toList();
        List<GameplayEventView> views = new java.util.ArrayList<>(visibleEvents.size());
        for (int index = 0; index < visibleEvents.size(); index++) {
            views.add(view(visibleEvents.get(index), index + 1));
        }
        return List.copyOf(views);
    }

    private static boolean visibleTo(GameEventEntity event, int seatNumber) {
        return event.getVisibility() == GameEvent.Audience.PUBLIC
                || Integer.valueOf(seatNumber).equals(event.getTargetSeat());
    }

    private GameplayEventView view(GameEventEntity event, int visibleOrder) {
        return new GameplayEventView(
                event.getSessionId(),
                event.getRevision(),
                visibleOrder,
                event.getEventType(),
                payload(event));
    }

    private JsonNode payload(GameEventEntity event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read gameplay event payload", exception);
        }
    }
}
