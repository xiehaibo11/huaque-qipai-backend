package com.nanbei.entertainment.backend.room.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.room.infrastructure.RoomParticipantRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomPlacementService {
    private final GameRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final GameSessionRepository sessionRepository;
    private final GameSessionSeatRepository seatRepository;

    public RoomPlacementService(
            GameRoomRepository roomRepository,
            RoomParticipantRepository participantRepository,
            GameSessionRepository sessionRepository,
            GameSessionSeatRepository seatRepository) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional(readOnly = true)
    public RoomPlacementView current(UUID userId) {
        return activeBoxRoom(userId)
                .map(room -> RoomPlacementView.from(room, userId))
                .orElseGet(RoomPlacementView::none);
    }

    @Transactional
    public RoomPlacementView leave(UUID userId, String roomNumber) {
        lockUser(userId);
        GameRoomEntity room =
                roomRepository
                        .findLockedByRoomNumber(roomNumber)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在"));
        requireBoxRoom(room);
        RoomParticipantId participantId = new RoomParticipantId(room.getId(), userId);
        if (!participantRepository.existsById(participantId)) {
            throw new ApiException(ErrorCode.ROOM_FORBIDDEN, "不在当前房间中");
        }
        if (room.getOwnerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.ROOM_FORBIDDEN, "房主请解散房间");
        }
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "已开局或已解散房间不能退出");
        }
        sessionRepository
                .findLockedByRoomId(room.getId())
                .ifPresent(
                        session -> {
                            if (session.getPhase() != GamePhase.WAITING) {
                                throw new ApiException(
                                        ErrorCode.ROOM_ILLEGAL_STATE,
                                        "已开局房间不能退出");
                            }
                            seatRepository
                                    .findByIdSessionIdAndUserId(session.getId(), userId)
                                    .ifPresent(seatRepository::delete);
                        });
        participantRepository.deleteById(participantId);
        return RoomPlacementView.none();
    }

    void lockUser(UUID userId) {
        roomRepository.acquireCreationLock(userId.toString());
    }

    void requireNoOtherActiveBoxRoom(UUID userId, UUID allowedRoomId) {
        activeBoxRoom(userId)
                .filter(room -> allowedRoomId == null || !room.getId().equals(allowedRoomId))
                .ifPresent(room -> throwAlreadyOpen(room, userId));
    }

    void requireBoxRoom(GameRoomEntity room) {
        if (room.getVenue() != RoomVenue.BOX) {
            throw new ApiException(ErrorCode.ROOM_NOT_FOUND, "房间不存在");
        }
    }

    private java.util.Optional<GameRoomEntity> activeBoxRoom(UUID userId) {
        return roomRepository
                .findActiveRoomsForParticipant(userId, RoomVenue.BOX, RoomStatus.DISSOLVED)
                .stream()
                .findFirst();
    }

    private static void throwAlreadyOpen(GameRoomEntity room, UUID userId) {
        throw new ApiException(
                ErrorCode.ROOM_ALREADY_OPEN,
                "当前已有未结束的房间",
                Map.of("placement", RoomPlacementView.from(room, userId)));
    }
}
