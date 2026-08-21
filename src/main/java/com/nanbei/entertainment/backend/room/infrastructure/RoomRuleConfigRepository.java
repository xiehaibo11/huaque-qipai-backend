package com.nanbei.entertainment.backend.room.infrastructure;

import com.nanbei.entertainment.backend.room.domain.RoomGameId;
import com.nanbei.entertainment.backend.room.domain.RoomRuleConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRuleConfigRepository
        extends JpaRepository<RoomRuleConfigEntity, RoomGameId> {}
