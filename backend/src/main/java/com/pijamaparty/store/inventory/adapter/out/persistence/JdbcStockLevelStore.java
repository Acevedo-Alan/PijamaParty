package com.pijamaparty.store.inventory.adapter.out.persistence;

import com.pijamaparty.store.inventory.application.AddStockLevels;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** Stock levels in table {@code stock_level} (V4), within the caller's transaction. */
@Repository
class JdbcStockLevelStore implements AddStockLevels {

    // Idempotent: a variant opened twice keeps its stock.
    private static final String INSERT_EMPTY = """
        INSERT INTO stock_level (variant_id, on_hand, updated_at)
        VALUES (:variantId, 0, :updatedAt)
        ON CONFLICT (variant_id) DO NOTHING
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcStockLevelStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void addEmpty(Collection<UUID> variantIds, Instant at) {
        OffsetDateTime updatedAt = at.atOffset(ZoneOffset.UTC);
        SqlParameterSource[] rows = variantIds.stream()
            .distinct()
            .sorted()
            .map(id -> new MapSqlParameterSource().addValue("variantId", id).addValue("updatedAt", updatedAt))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_EMPTY, rows);
    }
}
