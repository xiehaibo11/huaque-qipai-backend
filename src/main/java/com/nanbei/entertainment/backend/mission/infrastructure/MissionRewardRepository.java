package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.MissionRewardEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRewardRepository extends JpaRepository<MissionRewardEntity, UUID> {
    List<MissionRewardEntity> findByTaskCodeOrderByRewardOrderAsc(String taskCode);
    List<MissionRewardEntity> findByPageCodeAndMilestoneTargetOrderByRewardOrderAsc(
            String pageCode, long milestoneTarget);
}
