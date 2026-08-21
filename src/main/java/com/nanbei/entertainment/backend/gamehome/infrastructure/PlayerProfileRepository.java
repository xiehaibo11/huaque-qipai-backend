package com.nanbei.entertainment.backend.gamehome.infrastructure;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayerProfileRepository
        extends JpaRepository<PlayerProfileEntity, UUID> {
    @Query(value = "SELECT nextval('public_player_id_seq')", nativeQuery = true)
    long nextPublicPlayerId();

    Optional<PlayerProfileEntity> findByPublicPlayerId(long publicPlayerId);
}
