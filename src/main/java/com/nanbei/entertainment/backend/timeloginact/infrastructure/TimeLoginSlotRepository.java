package com.nanbei.entertainment.backend.timeloginact.infrastructure;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeLoginSlotRepository extends JpaRepository<TimeLoginSlotEntity, UUID> {
    List<TimeLoginSlotEntity> findByActivityIdOrderBySlotOrderAsc(UUID activityId);
}
