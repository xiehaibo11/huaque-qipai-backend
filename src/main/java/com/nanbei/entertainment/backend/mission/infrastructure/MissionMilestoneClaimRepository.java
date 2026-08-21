package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.MissionMilestoneClaimEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionMilestoneClaimRepository
        extends JpaRepository<MissionMilestoneClaimEntity, UUID> {
    List<MissionMilestoneClaimEntity> findByUserIdAndPageCodeAndCycleStartedAt(
            UUID userId, String pageCode, Instant cycleStartedAt);
    Optional<MissionMilestoneClaimEntity> findByUserIdAndPageCodeAndCycleStartedAtAndTarget(
            UUID userId, String pageCode, Instant cycleStartedAt, long target);
}
