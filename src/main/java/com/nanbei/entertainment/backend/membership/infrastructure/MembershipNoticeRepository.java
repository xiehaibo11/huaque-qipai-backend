package com.nanbei.entertainment.backend.membership.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MembershipNoticeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MembershipNoticeRepository(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Configuration> findActive() {
        return jdbcTemplate
                .query(
                        """
                        select version, title, items, change_notice,
                               agreement_title, agreement_url, updated_at
                        from membership_notice_configs
                        where id = 1 and active = true
                        """,
                        (rs, rowNum) ->
                                new Configuration(
                                        rs.getInt("version"),
                                        rs.getString("title"),
                                        parseItems(rs.getString("items")),
                                        rs.getString("change_notice"),
                                        rs.getString("agreement_title"),
                                        rs.getString("agreement_url"),
                                        rs.getObject("updated_at", OffsetDateTime.class)
                                                .toInstant()))
                .stream()
                .findFirst();
    }

    private List<String> parseItems(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> items = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode item : root) {
                    items.add(item.asText());
                }
            }
            return List.copyOf(items);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse membership notice items", exception);
        }
    }

    public record Configuration(
            int version,
            String title,
            List<String> items,
            String changeNotice,
            String agreementTitle,
            String agreementUrl,
            Instant updatedAt) {}
}
