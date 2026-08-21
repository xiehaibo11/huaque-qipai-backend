package com.nanbei.entertainment.backend.roomtools.infrastructure;

import com.nanbei.entertainment.backend.roomtools.domain.RoomToolMessageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomToolMessageRepository extends JpaRepository<RoomToolMessageEntity, UUID> {
    @Query(
            value =
                    "SELECT * FROM room_tool_messages WHERE session_id = :sessionId "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<RoomToolMessageEntity> findLatest(
            @Param("sessionId") UUID sessionId, @Param("limit") int limit);

    Optional<RoomToolMessageEntity> findByIdAndSessionId(UUID id, UUID sessionId);
}
