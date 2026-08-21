package com.nanbei.entertainment.backend.membership.infrastructure;

import com.nanbei.entertainment.backend.membership.domain.MembershipRewardGrantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRewardGrantRepository
        extends JpaRepository<MembershipRewardGrantEntity, UUID> {
    boolean existsByUserIdAndSourceTypeAndSourceIdAndRewardCodeAndDisplayName(
            UUID userId,
            String sourceType,
            String sourceId,
            String rewardCode,
            String displayName);
}
