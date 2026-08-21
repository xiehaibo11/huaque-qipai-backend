package com.nanbei.entertainment.backend.room.infrastructure;

import com.nanbei.entertainment.backend.room.domain.RoomParticipantEntity;
import com.nanbei.entertainment.backend.room.domain.RoomParticipantId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomParticipantRepository
        extends JpaRepository<RoomParticipantEntity, RoomParticipantId> {
    long countByIdRoomId(UUID roomId);

    List<RoomParticipantEntity> findByIdRoomIdOrderByIdUserId(UUID roomId);
}
