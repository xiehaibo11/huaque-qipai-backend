package com.nanbei.entertainment.backend.gameplay.infrastructure;

import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionSeatRepository
        extends JpaRepository<GameSessionSeatEntity, GameSessionSeatId> {
    List<GameSessionSeatEntity> findByIdSessionIdOrderByIdSeatNumber(UUID sessionId);

    Optional<GameSessionSeatEntity> findByIdSessionIdAndUserId(UUID sessionId, UUID userId);
}
