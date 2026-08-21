package com.nanbei.entertainment.backend.membership.infrastructure;

import com.nanbei.entertainment.backend.membership.domain.MembershipDailyGiftClaimEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipDailyGiftClaimRepository
        extends JpaRepository<MembershipDailyGiftClaimEntity, UUID> {
    Optional<MembershipDailyGiftClaimEntity> findByUserIdAndClaimedOn(
            UUID userId, LocalDate claimedOn);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireDailyClaimLock(@Param("lockKey") String lockKey);
}
