package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresLockContention {
    private PostgresLockContention() {}

    static void lockRefreshToken(Connection connection, String tokenHash)
            throws Exception {
        try (var statement =
                connection.prepareStatement(
                        """
                        SELECT id
                        FROM refresh_tokens
                        WHERE token_hash = ?
                        FOR UPDATE
                        """)) {
            statement.setString(1, tokenHash);
            assertThat(statement.executeQuery().next()).isTrue();
        }
    }

    static void lockAdvisoryKey(Connection connection, String lockKey)
            throws Exception {
        try (var statement =
                connection.prepareStatement(
                        """
                        SELECT pg_advisory_xact_lock(
                            hashtextextended(CAST(? AS text), 0)
                        )
                        """)) {
            statement.setString(1, lockKey);
            assertThat(statement.executeQuery().next()).isTrue();
        }
    }

    static boolean awaitDatabaseLockWaiters(
            JdbcTemplate jdbcTemplate, int count, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Long waiters =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT count(*)
                            FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND wait_event_type = 'Lock'
                            """,
                            Long.class);
            if (waiters != null && waiters >= count) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
