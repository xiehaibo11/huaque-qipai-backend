package com.nanbei.entertainment.backend.room.infrastructure;

import com.nanbei.entertainment.backend.room.domain.RoomCardLedgerEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomCardLedgerRepository
        extends JpaRepository<RoomCardLedgerEntity, UUID> {}
