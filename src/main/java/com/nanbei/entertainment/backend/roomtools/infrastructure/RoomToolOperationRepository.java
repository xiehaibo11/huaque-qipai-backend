package com.nanbei.entertainment.backend.roomtools.infrastructure;

import com.nanbei.entertainment.backend.roomtools.domain.RoomToolOperationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomToolOperationRepository
        extends JpaRepository<RoomToolOperationEntity, UUID> {
    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireOperationLock(@Param("lockKey") String lockKey);

    Optional<RoomToolOperationEntity> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);
}
