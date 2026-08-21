package com.nanbei.entertainment.backend.goldroom.infrastructure;

import com.nanbei.entertainment.backend.goldroom.domain.GoldGameEntity;
import com.nanbei.entertainment.backend.goldroom.domain.GoldGameId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoldGameRepository extends JpaRepository<GoldGameEntity, GoldGameId> {
    List<GoldGameEntity> findByIdLobbyIdAndEnabledTrueOrderBySortOrder(long lobbyId);
}
