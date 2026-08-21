package com.nanbei.entertainment.backend.gameplay.infrastructure;

import com.nanbei.entertainment.backend.gameplay.domain.GameCommandEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameCommandRepository extends JpaRepository<GameCommandEntity, UUID> {
    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireCommandLock(@Param("lockKey") String lockKey);

    Optional<GameCommandEntity> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);
}
