package com.nanbei.entertainment.backend.gamehome.infrastructure;

import com.nanbei.entertainment.backend.gamehome.domain.LobbyAnnouncementEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LobbyAnnouncementRepository
        extends JpaRepository<LobbyAnnouncementEntity, Long> {
    List<LobbyAnnouncementEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
