package com.nanbei.entertainment.backend.membership.infrastructure;

import com.nanbei.entertainment.backend.membership.domain.GoldMembershipCardEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoldMembershipCardRepository
        extends JpaRepository<GoldMembershipCardEntity, UUID> {
    List<GoldMembershipCardEntity> findAllByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select card from GoldMembershipCardEntity card "
                    + "where card.userId = :userId and card.productCode = :productCode")
    Optional<GoldMembershipCardEntity> findLocked(
            @Param("userId") UUID userId,
            @Param("productCode") String productCode);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireCardLock(@Param("lockKey") String lockKey);
}
