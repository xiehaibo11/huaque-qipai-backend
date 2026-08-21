package com.nanbei.entertainment.backend.friend.infrastructure;

import com.nanbei.entertainment.backend.friend.domain.FriendApplicationEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FriendApplicationRepository
        extends JpaRepository<FriendApplicationEntity, UUID> {
    boolean existsByRequesterIdAndTargetIdAndStatus(
            UUID requesterId, UUID targetId, FriendApplicationStatus status);

    Optional<FriendApplicationEntity> findByRequesterIdAndTargetIdAndStatus(
            UUID requesterId, UUID targetId, FriendApplicationStatus status);

    @Query(
            """
            SELECT new com.nanbei.entertainment.backend.friend.infrastructure.FriendApplicationRow(
                a.id, p.publicPlayerId, u.displayName, p.avatarKey, a.createdAt)
            FROM FriendApplicationEntity a
            JOIN UserEntity u ON u.id = a.requesterId
            JOIN PlayerProfileEntity p ON p.userId = a.requesterId
            WHERE a.targetId = :targetId
                AND a.status = com.nanbei.entertainment.backend.friend.domain.FriendApplicationStatus.PENDING
            ORDER BY a.createdAt DESC
            """)
    List<FriendApplicationRow> findPendingRows(UUID targetId);
}
