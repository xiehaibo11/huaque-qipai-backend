package com.nanbei.entertainment.backend.gameplay.infrastructure;

import com.nanbei.entertainment.backend.gameplay.domain.GameEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventRepository extends JpaRepository<GameEventEntity, Long> {
    List<GameEventEntity> findBySessionIdAndRevisionGreaterThanOrderByRevisionAscEventOrderAsc(
            java.util.UUID sessionId, long revision);
}
