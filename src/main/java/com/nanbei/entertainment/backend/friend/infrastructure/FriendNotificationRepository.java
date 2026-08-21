package com.nanbei.entertainment.backend.friend.infrastructure;

import com.nanbei.entertainment.backend.friend.domain.FriendNotificationEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FriendNotificationRepository
        extends JpaRepository<FriendNotificationEntity, UUID> {
    boolean existsByUserIdAndActorIdAndTypeAndCreatedAtAfter(
            UUID userId,
            UUID actorId,
            FriendNotificationType type,
            Instant createdAfter);

    @Query(
            """
            SELECT n.userId FROM FriendNotificationEntity n
            WHERE n.actorId = :actorId AND n.type = :type
                AND n.createdAt > :createdAfter
                AND n.userId IN :userIds
            """)
    Set<UUID> findUserIdsWithNotificationAfter(
            UUID actorId,
            FriendNotificationType type,
            Instant createdAfter,
            Collection<UUID> userIds);

    @Query(
            """
            SELECT new com.nanbei.entertainment.backend.friend.infrastructure.FriendNotificationRow(
                n.id, n.type, p.publicPlayerId, u.displayName, n.createdAt)
            FROM FriendNotificationEntity n
            JOIN UserEntity u ON u.id = n.actorId
            JOIN PlayerProfileEntity p ON p.userId = n.actorId
            WHERE n.userId = :userId
                AND (:unreadOnly = false OR n.readAt IS NULL)
            ORDER BY n.createdAt DESC
            """)
    List<FriendNotificationRow> findRows(UUID userId, boolean unreadOnly);

    @Modifying
    @Query(
            """
            UPDATE FriendNotificationEntity n
            SET n.readAt = :now
            WHERE n.userId = :userId AND n.readAt IS NULL
            """)
    int markAllRead(UUID userId, Instant now);
}
