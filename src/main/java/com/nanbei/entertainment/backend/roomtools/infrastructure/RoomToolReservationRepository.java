package com.nanbei.entertainment.backend.roomtools.infrastructure;

import com.nanbei.entertainment.backend.roomtools.application.RoomToolType;
import com.nanbei.entertainment.backend.roomtools.domain.RoomToolReservationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomToolReservationRepository
        extends JpaRepository<RoomToolReservationEntity, UUID> {
    List<RoomToolReservationEntity> findBySessionIdAndUserIdAndActiveTrueOrderByToolType(
            UUID sessionId, UUID userId);

    Optional<RoomToolReservationEntity> findBySessionIdAndUserIdAndToolTypeAndTargetRound(
            UUID sessionId, UUID userId, RoomToolType toolType, int targetRound);
}
