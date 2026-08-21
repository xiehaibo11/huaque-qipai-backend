package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.MissionEventType;
import com.nanbei.entertainment.backend.mission.domain.MissionTaskDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionTaskDefinitionRepository
        extends JpaRepository<MissionTaskDefinitionEntity, String> {
    List<MissionTaskDefinitionEntity> findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc(String pageCode);
    List<MissionTaskDefinitionEntity> findByEventTypeAndEnabledTrue(MissionEventType eventType);
    Optional<MissionTaskDefinitionEntity> findByTaskCodeAndEnabledTrue(String taskCode);
}
