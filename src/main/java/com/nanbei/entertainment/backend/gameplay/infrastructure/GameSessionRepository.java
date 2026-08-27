package com.nanbei.entertainment.backend.gameplay.infrastructure;

import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSessionEntity, UUID> {
    Optional<GameSessionEntity> findByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from GameSessionEntity session where session.roomId = :roomId")
    Optional<GameSessionEntity> findLockedByRoomId(@Param("roomId") UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from GameSessionEntity session where session.id = :id")
    Optional<GameSessionEntity> findLockedById(@Param("id") UUID id);

    @Query(
            "select session.id from GameSessionEntity session"
                    + " where session.phase = com.nanbei.entertainment.backend.gameplay.domain.GamePhase.PLAYING")
    List<UUID> findPlayingIds();
}
