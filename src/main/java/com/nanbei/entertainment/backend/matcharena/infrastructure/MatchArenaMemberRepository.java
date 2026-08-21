package com.nanbei.entertainment.backend.matcharena.infrastructure;

import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMemberEntity;
import com.nanbei.entertainment.backend.matcharena.domain.MatchArenaMemberStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchArenaMemberRepository
        extends JpaRepository<MatchArenaMemberEntity, UUID> {
    @Query(
            value =
                    """
                    SELECT member.*
                    FROM match_arena_members member
                    JOIN match_arenas arena ON arena.id = member.arena_id
                    WHERE member.user_id = :userId
                      AND member.status = 'ACTIVE'
                      AND arena.status <> 'DISSOLVED'
                    ORDER BY arena.created_at DESC, arena.id DESC
                    """,
            nativeQuery = true)
    List<MatchArenaMemberEntity> findVisibleByUserId(@Param("userId") UUID userId);

    long countByArenaIdAndStatus(UUID arenaId, MatchArenaMemberStatus status);

    @Query(
            value =
                    """
                    SELECT count(*)
                    FROM match_arena_members member
                    JOIN app_users app_user ON app_user.id = member.user_id
                    WHERE member.arena_id = :arenaId
                      AND member.status = 'ACTIVE'
                      AND app_user.last_active_at >= now() - interval '5 minutes'
                    """,
            nativeQuery = true)
    long countOnlineByArenaId(@Param("arenaId") UUID arenaId);
}
