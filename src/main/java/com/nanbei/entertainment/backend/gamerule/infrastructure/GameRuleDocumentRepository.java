package com.nanbei.entertainment.backend.gamerule.infrastructure;

import com.nanbei.entertainment.backend.gamerule.domain.GameRuleDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GameRuleDocumentRepository {
    private static final String FIND_BY_GAME_ID =
            """
            select document.game_id,
                   document.title,
                   block.value ->> 'type' as block_type,
                   block.value ->> 'text' as block_text
            from game_rule_documents document
            left join lateral jsonb_array_elements(document.blocks)
                with ordinality as block(value, position) on true
            where document.game_id = ?
            order by block.position
            """;

    private final JdbcTemplate jdbcTemplate;

    public GameRuleDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GameRuleDocument> findByGameId(long gameId) {
        List<Row> rows =
                jdbcTemplate.query(
                        FIND_BY_GAME_ID,
                        (result, rowNumber) ->
                                new Row(
                                        result.getLong("game_id"),
                                        result.getString("title"),
                                        result.getString("block_type"),
                                        result.getString("block_text")),
                        gameId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<GameRuleDocument.Block> blocks = new ArrayList<>();
        for (Row row : rows) {
            if (row.blockType() != null) {
                blocks.add(new GameRuleDocument.Block(row.blockType(), row.blockText()));
            }
        }
        Row first = rows.get(0);
        return Optional.of(new GameRuleDocument(first.gameId(), first.title(), blocks));
    }

    private record Row(long gameId, String title, String blockType, String blockText) {}
}
