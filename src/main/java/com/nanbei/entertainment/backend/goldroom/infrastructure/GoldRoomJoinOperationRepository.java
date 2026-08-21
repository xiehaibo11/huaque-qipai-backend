package com.nanbei.entertainment.backend.goldroom.infrastructure;

import com.nanbei.entertainment.backend.goldroom.domain.GoldRoomJoinOperationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoldRoomJoinOperationRepository
        extends JpaRepository<GoldRoomJoinOperationEntity, UUID> {
    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireJoinLock(@Param("lockKey") String lockKey);

    Optional<GoldRoomJoinOperationEntity> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);
}
