package com.nanbei.entertainment.backend.goldroom.infrastructure;

import com.nanbei.entertainment.backend.goldroom.domain.GoldGameLevelEntity;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameLevelId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoldGameLevelRepository
        extends JpaRepository<GoldGameLevelEntity, GoldGameLevelId> {
    List<GoldGameLevelEntity> findByIdLobbyIdAndIdGameIdAndEnabledTrueOrderBySortOrder(
            long lobbyId, long gameId);
}
