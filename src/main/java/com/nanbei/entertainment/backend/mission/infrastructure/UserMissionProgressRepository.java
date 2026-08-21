package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.UserMissionProgressEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserMissionProgressRepository
        extends JpaRepository<UserMissionProgressEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserMissionProgressEntity p where p.userId=:userId and p.taskCode=:taskCode and p.cycleStartedAt=:cycleStart")
    Optional<UserMissionProgressEntity> findLocked(
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("cycleStart") Instant cycleStart);

    Optional<UserMissionProgressEntity> findByUserIdAndTaskCodeAndCycleStartedAt(
            UUID userId, String taskCode, Instant cycleStartedAt);

    List<UserMissionProgressEntity> findByUserIdAndCycleStartedAt(UUID userId, Instant cycleStartedAt);
}
