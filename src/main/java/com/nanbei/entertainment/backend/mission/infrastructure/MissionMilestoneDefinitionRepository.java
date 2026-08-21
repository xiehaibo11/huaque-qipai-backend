package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.MissionMilestoneDefinitionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionMilestoneDefinitionRepository
        extends JpaRepository<MissionMilestoneDefinitionEntity, UUID> {
    List<MissionMilestoneDefinitionEntity> findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc(String pageCode);
    Optional<MissionMilestoneDefinitionEntity> findByPageCodeAndTargetAndEnabledTrue(String pageCode, long target);
}
