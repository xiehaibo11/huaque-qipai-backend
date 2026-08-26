package com.nanbei.entertainment.backend.membership.infrastructure;

import com.nanbei.entertainment.backend.membership.domain.GoldMembershipCardClaimEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoldMembershipCardClaimRepository
        extends JpaRepository<GoldMembershipCardClaimEntity, UUID> {
    List<GoldMembershipCardClaimEntity> findAllByUserIdAndClaimedOn(
            UUID userId, LocalDate claimedOn);

    boolean existsByUserIdAndProductCodeAndClaimedOn(
            UUID userId, String productCode, LocalDate claimedOn);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireClaimLock(@Param("lockKey") String lockKey);
}
