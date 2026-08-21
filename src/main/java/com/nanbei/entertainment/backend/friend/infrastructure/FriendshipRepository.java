package com.nanbei.entertainment.backend.friend.infrastructure;

import com.nanbei.entertainment.backend.friend.domain.FriendshipEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendshipId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FriendshipRepository
        extends JpaRepository<FriendshipEntity, FriendshipId> {
    @Query(
            """
            SELECT new com.nanbei.entertainment.backend.friend.infrastructure.FriendRow(
                f.friendId,
                p.publicPlayerId,
                u.displayName,
                p.avatarKey,
                u.lastActiveAt,
                f.shielded,
                CASE
                    WHEN room.status = com.nanbei.entertainment.backend.room.domain.RoomStatus.OPEN
                        AND room.playerCount > (
                            SELECT COUNT(waitParticipant)
                            FROM RoomParticipantEntity waitParticipant
                            WHERE waitParticipant.id.roomId = room.id
                        )
                        THEN 8
                    WHEN room.status = com.nanbei.entertainment.backend.room.domain.RoomStatus.CHARGED
                        THEN 2
                    ELSE 0
                END,
                room.playerCount,
                (
                    SELECT COUNT(countParticipant)
                    FROM RoomParticipantEntity countParticipant
                    WHERE countParticipant.id.roomId = room.id
                ),
                room.roomNumber,
                room.gameId)
            FROM FriendshipEntity f
            JOIN UserEntity u ON u.id = f.friendId
            JOIN PlayerProfileEntity p ON p.userId = f.friendId
            LEFT JOIN RoomParticipantEntity participant
                ON participant.id.userId = f.friendId
                AND participant.joinedAt = (
                    SELECT MAX(activeParticipant.joinedAt)
                    FROM RoomParticipantEntity activeParticipant
                    JOIN GameRoomEntity activeRoom
                        ON activeRoom.id = activeParticipant.id.roomId
                    WHERE activeParticipant.id.userId = f.friendId
                        AND activeRoom.status <> com.nanbei.entertainment.backend.room.domain.RoomStatus.DISSOLVED
                )
            LEFT JOIN GameRoomEntity room
                ON room.id = participant.id.roomId
                AND room.status <> com.nanbei.entertainment.backend.room.domain.RoomStatus.DISSOLVED
            WHERE f.userId = :userId
            ORDER BY CASE
                    WHEN room.status = com.nanbei.entertainment.backend.room.domain.RoomStatus.OPEN
                        AND room.playerCount > (
                            SELECT COUNT(orderParticipant)
                            FROM RoomParticipantEntity orderParticipant
                            WHERE orderParticipant.id.roomId = room.id
                        )
                        THEN 8
                    WHEN room.status = com.nanbei.entertainment.backend.room.domain.RoomStatus.CHARGED
                        THEN 2
                    WHEN u.lastActiveAt >= :onlineSince THEN 4
                    ELSE 1
                END DESC,
                u.lastActiveAt DESC NULLS LAST
            """)
    Slice<FriendRow> findFriendRows(
            UUID userId, Instant onlineSince, Pageable pageable);

    boolean existsByUserIdAndFriendId(UUID userId, UUID friendId);

    @Query(
            """
            SELECT f.friendId FROM FriendshipEntity f
            JOIN UserEntity u ON u.id = f.friendId
            WHERE f.userId = :userId AND f.shielded = false
                AND u.lastActiveAt >= :onlineSince
            """)
    List<UUID> findOnlineUnshieldedFriendIds(
            UUID userId, Instant onlineSince);

    Optional<FriendshipEntity> findByUserIdAndFriendId(
            UUID userId, UUID friendId);

    void deleteByUserIdAndFriendId(UUID userId, UUID friendId);
}
