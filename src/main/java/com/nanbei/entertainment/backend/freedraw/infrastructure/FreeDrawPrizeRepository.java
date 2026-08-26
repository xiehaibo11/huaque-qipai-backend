package com.nanbei.entertainment.backend.freedraw.infrastructure;

import com.nanbei.entertainment.backend.freedraw.domain.FreeDrawPrizeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeDrawPrizeRepository extends JpaRepository<FreeDrawPrizeEntity, UUID> {
    List<FreeDrawPrizeEntity> findByActivityIdAndEnabledTrueOrderByDisplayOrderAsc(UUID activityId);
}
