package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.MissionClaimRequestEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionClaimRequestRepository
        extends JpaRepository<MissionClaimRequestEntity, UUID> {
    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireIdempotencyLock(@Param("lockKey") String lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MissionClaimRequestEntity c where c.userId=:userId and c.idempotencyKey=:key")
    Optional<MissionClaimRequestEntity> findLockedByKey(
            @Param("userId") UUID userId, @Param("key") String key);
}
