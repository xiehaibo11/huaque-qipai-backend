package com.nanbei.entertainment.backend.room.infrastructure;

import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import com.nanbei.entertainment.backend.room.domain.RoomVenue;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRoomRepository extends JpaRepository<GameRoomEntity, UUID> {
    Optional<GameRoomEntity> findByRoomNumber(String roomNumber);

    Optional<GameRoomEntity> findByOwnerUserIdAndCreationIdempotencyKey(
            UUID ownerUserId, String creationIdempotencyKey);

    Optional<GameRoomEntity> findByOwnerUserIdAndStatusNot(
            UUID ownerUserId, RoomStatus status);

    @Query(
            """
            select room
            from GameRoomEntity room
            where room.venue = :venue
              and room.status <> :dissolved
              and exists (
                  select participant.id.roomId
                  from RoomParticipantEntity participant
                  where participant.id.roomId = room.id
                    and participant.id.userId = :userId
              )
            order by room.createdAt desc
            """)
    List<GameRoomEntity> findActiveRoomsForParticipant(
            @Param("userId") UUID userId,
            @Param("venue") RoomVenue venue,
            @Param("dissolved") RoomStatus dissolved);

    Optional<GameRoomEntity>
            findFirstByOwnerUserIdAndGameIdAndRoomModeAndCreationRequestHashAndStatusNotOrderByCreatedAtDesc(
                    UUID ownerUserId,
                    long gameId,
                    int roomMode,
                    String creationRequestHash,
                    RoomStatus status);

    @Query(
            """
            select room
            from GameRoomEntity room
            where room.venue = 'GOLD'
              and room.gameId = :gameId
              and room.roomMode = :roomMode
              and room.creationRequestHash = :creationRequestHash
              and room.status <> :status
              and exists (
                  select participant.id.roomId
                  from RoomParticipantEntity participant
                  where participant.id.roomId = room.id
                    and participant.id.userId = :userId
              )
            order by room.createdAt desc
            """)
    List<GameRoomEntity> findLiveRoomsForParticipantAndQaMatch(
            @Param("userId") UUID userId,
            @Param("gameId") long gameId,
            @Param("roomMode") int roomMode,
            @Param("creationRequestHash") String creationRequestHash,
            @Param("status") RoomStatus status);

    @Query(
            """
            select room
            from GameRoomEntity room
            where room.venue = 'GOLD'
              and room.gameId = :gameId
              and room.roomMode = :roomMode
              and room.creationRequestHash = :creationRequestHash
              and room.status = :status
              and (select count(participant.id.roomId)
                   from RoomParticipantEntity participant
                   where participant.id.roomId = room.id) < room.playerCount
            order by room.createdAt asc
            """)
    List<GameRoomEntity> findMatchableGoldRooms(
            @Param("gameId") long gameId,
            @Param("roomMode") int roomMode,
            @Param("creationRequestHash") String creationRequestHash,
            @Param("status") RoomStatus status);

    @Query(
            """
            select count(participant)
            from RoomParticipantEntity participant, GameRoomEntity room
            where participant.id.roomId = room.id
              and room.venue = 'GOLD'
              and room.gameId = :gameId
              and room.roomMode = :roomMode
              and room.creationRequestHash = :creationRequestHash
              and room.status <> :dissolved
            """)
    long countActiveGoldPlayers(
            @Param("gameId") long gameId,
            @Param("roomMode") int roomMode,
            @Param("creationRequestHash") String creationRequestHash,
            @Param("dissolved") RoomStatus dissolved);

    @Query(
            """
            select room
            from GameRoomEntity room
            where room.venue = 'GOLD'
              and room.status = :status
              and room.creationRequestHash like :hashPrefix
              and room.createdAt < :cutoff
              and (select count(participant.id.roomId)
                   from RoomParticipantEntity participant
                   where participant.id.roomId = room.id) < room.playerCount
            """)
    List<GameRoomEntity> findTimedOutGoldMatchingRooms(
            @Param("status") RoomStatus status,
            @Param("hashPrefix") String hashPrefix,
            @Param("cutoff") Instant cutoff);

    boolean existsByRoomNumber(String roomNumber);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireCreationLock(@Param("lockKey") String lockKey);

    @Query(
            value =
                    """
                    SELECT lpad((
                        ((nextval('room_number_seq') * 7919) % 900000) + 100000
                    )::text, 6, '0')
                    """,
            nativeQuery = true)
    String nextRoomNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from GameRoomEntity room where room.roomNumber = :roomNumber")
    Optional<GameRoomEntity> findLockedByRoomNumber(
            @Param("roomNumber") String roomNumber);
}
