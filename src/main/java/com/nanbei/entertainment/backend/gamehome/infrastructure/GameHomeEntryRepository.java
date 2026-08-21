package com.nanbei.entertainment.backend.gamehome.infrastructure;

import com.nanbei.entertainment.backend.gamehome.domain.GameHomeEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameHomeEntryRepository
        extends JpaRepository<GameHomeEntryEntity, String> {
    List<GameHomeEntryEntity> findByEnabledTrueOrderBySortOrderAsc();
}
