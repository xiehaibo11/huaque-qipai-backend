package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.domain.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshTokenEntity, UUID> {
    @Query(
            "select refreshToken.userId from RefreshTokenEntity refreshToken "
                    + "where refreshToken.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select refreshToken from RefreshTokenEntity refreshToken "
                    + "where refreshToken.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findLockedByTokenHash(
            @Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

    List<RefreshTokenEntity> findByUserId(UUID userId);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireUserSessionLock(@Param("lockKey") String lockKey);

    @Modifying
    @Query(
            "update RefreshTokenEntity token set token.revokedAt = :now "
                    + "where token.userId = :userId and token.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") java.time.Instant now);
}
