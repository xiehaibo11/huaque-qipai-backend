package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.domain.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select refreshToken from RefreshTokenEntity refreshToken "
                    + "where refreshToken.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findLockedByTokenHash(
            @Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);
}
