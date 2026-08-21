package com.nanbei.entertainment.backend.timeloginact.infrastructure;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginWheelSliceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeLoginWheelSliceRepository
        extends JpaRepository<TimeLoginWheelSliceEntity, UUID> {
    List<TimeLoginWheelSliceEntity> findByActivityIdOrderBySliceIndexAsc(UUID activityId);
}
