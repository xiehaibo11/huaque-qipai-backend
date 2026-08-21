package com.nanbei.entertainment.backend.matcharena.infrastructure;

import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaEntity;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaLevel;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchArenaRepository extends JpaRepository<MatchArenaEntity, UUID> {
    Optional<MatchArenaEntity> findByOwnerUserIdAndIdempotencyKey(
            UUID ownerUserId, String idempotencyKey);

    long countByOwnerUserIdAndLevelAndStatusNot(
            UUID ownerUserId, MatchArenaLevel level, MatchArenaStatus status);

    @Query(
            value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireOwnerCreateLock(@Param("lockKey") String lockKey);

    @Query(value = "SELECT nextval('match_arena_number_seq')", nativeQuery = true)
    long nextArenaNumber();
}
