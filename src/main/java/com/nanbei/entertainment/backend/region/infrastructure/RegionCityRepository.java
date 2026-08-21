package com.nanbei.entertainment.backend.region.infrastructure;

import com.nanbei.entertainment.backend.region.domain.RegionCityEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionCityRepository
        extends JpaRepository<RegionCityEntity, String> {
    List<RegionCityEntity> findByEnabledTrueOrderBySortOrderAsc();
}
