package com.nanbei.entertainment.backend.room.infrastructure;

import com.nanbei.entertainment.backend.room.domain.RoomGameEntity;
import com.nanbei.entertainment.backend.room.domain.RoomGameId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomGameRepository extends JpaRepository<RoomGameEntity, RoomGameId> {
    List<RoomGameEntity> findByIdLobbyIdAndEnabledTrueOrderBySortOrder(long lobbyId);
}
