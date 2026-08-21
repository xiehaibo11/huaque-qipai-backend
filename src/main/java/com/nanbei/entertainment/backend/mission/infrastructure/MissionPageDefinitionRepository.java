package com.nanbei.entertainment.backend.mission.infrastructure;

import com.nanbei.entertainment.backend.mission.domain.MissionPageDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionPageDefinitionRepository
        extends JpaRepository<MissionPageDefinitionEntity, String> {
    List<MissionPageDefinitionEntity> findByEnabledTrueOrderByDisplayOrderAsc();
    Optional<MissionPageDefinitionEntity> findByPageCodeAndEnabledTrue(String pageCode);
}
