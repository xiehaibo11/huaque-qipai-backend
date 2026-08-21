package com.nanbei.entertainment.backend.membership.infrastructure;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipGoldStatisticsRepository {
    private final JdbcTemplate jdbcTemplate;

    public MembershipGoldStatisticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findGameIds(UUID userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(
                """
                select distinct game_id
                from membership_gold_statistics
                where user_id = ?
                  and played_on between ? and ?
                order by game_id
                """,
                (rs, rowNum) -> rs.getLong("game_id"),
                userId,
                Date.valueOf(startDate),
                Date.valueOf(endDate));
    }

    public Aggregate aggregate(UUID userId, long gameId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.queryForObject(
                """
                select
                    coalesce(sum(fight_count), 0) as fight_count,
                    coalesce(sum(win_count), 0) as win_count,
                    coalesce(sum(coin_delta), 0) as coin_delta
                from membership_gold_statistics
                where user_id = ?
                  and played_on between ? and ?
                  and (? = 0 or game_id = ?)
                """,
                (rs, rowNum) ->
                        new Aggregate(
                                rs.getLong("fight_count"),
                                rs.getLong("win_count"),
                                rs.getLong("coin_delta")),
                userId,
                Date.valueOf(startDate),
                Date.valueOf(endDate),
                gameId,
                gameId);
    }

    public record Aggregate(long fightCnt, long winCnt, long winScore) {}
}
