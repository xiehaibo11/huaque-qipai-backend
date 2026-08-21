package com.nanbei.entertainment.backend.timeloginact.infrastructure;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginClaimEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeLoginClaimRepository extends JpaRepository<TimeLoginClaimEntity, UUID> {
    List<TimeLoginClaimEntity> findByUserIdAndActivityIdAndActivityDate(
            UUID userId, UUID activityId, LocalDate activityDate);
}
