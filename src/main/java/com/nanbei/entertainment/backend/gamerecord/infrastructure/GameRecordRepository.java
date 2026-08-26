package com.nanbei.entertainment.backend.gamerecord.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GameRecordRepository {
    private static final ZoneId ORIGINAL_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;

    public GameRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Row> find(UUID userId, LocalDate date, long gameId, boolean gold) {
        Instant start = date.atStartOfDay(ORIGINAL_TIME_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ORIGINAL_TIME_ZONE).toInstant();
        return jdbcTemplate.query(
                """
                select session.id as session_id,
                       room.room_number,
                       session.game_id,
                       room.play_count,
                       session.round_number,
                       session.updated_at,
                       seat.seat_number,
                       seat.user_id,
                       profile.public_player_id,
                       app_user.display_name,
                       coalesce((
                           select sum(value::bigint)
                           from jsonb_array_elements_text(coalesce(
                               session.state #> array[
                                   'qaRound', 'totalResult', 'seats',
                                   seat.seat_number::text, 'roundWinLost'],
                               '[]'::jsonb)) as value
                       ), 0) as score_delta,
                       room.owner_user_id = seat.user_id as host
                from game_sessions session
                join game_rooms room on room.id = session.room_id
                join game_session_seats viewer
                  on viewer.session_id = session.id and viewer.user_id = ?
                join game_session_seats seat on seat.session_id = session.id
                join app_users app_user on app_user.id = seat.user_id
                join player_profiles profile on profile.user_id = seat.user_id
                where session.round_number > 0
                  and session.phase in ('ROUND_RESULT', 'COMPLETED')
                  and session.updated_at >= ? and session.updated_at < ?
                  and (? = 0 or session.game_id = ?)
                  and ((? and room.room_mode = 50)
                       or (not ? and room.room_mode <> 50))
                order by session.updated_at desc, seat.seat_number
                """,
                (rs, rowNumber) ->
                        new Row(
                                rs.getObject("session_id", UUID.class),
                                rs.getString("room_number"),
                                rs.getLong("game_id"),
                                rs.getInt("play_count"),
                                rs.getInt("round_number"),
                                rs.getTimestamp("updated_at").toInstant(),
                                rs.getInt("seat_number"),
                                rs.getObject("user_id", UUID.class),
                                rs.getLong("public_player_id"),
                                rs.getString("display_name"),
                                rs.getLong("score_delta"),
                                rs.getBoolean("host")),
                userId,
                Timestamp.from(start),
                Timestamp.from(end),
                gameId,
                gameId,
                gold,
                gold);
    }

    public record Row(
            UUID sessionId,
            String roomNumber,
            long gameId,
            int totalRounds,
            int finishedRounds,
            Instant finishedAt,
            int seat,
            UUID userId,
            long publicPlayerId,
            String displayName,
            long score,
            boolean host) {}
}
