package com.nanbei.entertainment.backend.timeloginact.infrastructure;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginOperationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeLoginOperationRepository
        extends JpaRepository<TimeLoginOperationEntity, UUID> {
    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireOperationLock(@Param("lockKey") String lockKey);

    Optional<TimeLoginOperationEntity> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);
}
