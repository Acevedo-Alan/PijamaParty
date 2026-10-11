package com.pijamaparty.store.inventory.adapter.out.persistence;

import com.pijamaparty.store.inventory.application.AddStockLevels;
import com.pijamaparty.store.inventory.application.LoadStockLevels;
import com.pijamaparty.store.inventory.application.SaveStockLevels;
import com.pijamaparty.store.inventory.application.StockHistory;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** Stock levels and movements in tables {@code stock_level} and {@code stock_movement} (V4, V8), in the caller's transaction. */
@Repository
class JdbcStockLevelStore implements AddStockLevels, LoadStockLevels, SaveStockLevels, StockHistory {

    // Idempotent: a variant opened twice keeps its stock.
    private static final String INSERT_EMPTY = """
        INSERT INTO stock_level (variant_id, on_hand, updated_at)
        VALUES (:variantId, 0, :updatedAt)
        ON CONFLICT (variant_id) DO NOTHING
        """;

    private static final String LOCK_LEVEL = """
        SELECT l.on_hand, l.updated_at,
               EXISTS (SELECT 1 FROM stock_movement m WHERE m.variant_id = l.variant_id) AS has_movements
        FROM stock_level l
        WHERE l.variant_id = :variantId
        FOR UPDATE OF l
        """;

    private static final String SELECT_ON_HAND = """
        SELECT on_hand FROM stock_level WHERE variant_id = :variantId
        """;

    private static final String MOVEMENT_COLUMNS = """
        SELECT id, variant_id, type, quantity, note, user_id, adjustment_key, created_at
        FROM stock_movement
        """;

    private static final String SELECT_ADJUSTMENT = MOVEMENT_COLUMNS + """
        WHERE variant_id = :variantId AND adjustment_key = :adjustmentKey
        """;

    // The id breaks ties between movements of the same instant, so pages never overlap.
    private static final String SELECT_HISTORY = MOVEMENT_COLUMNS + """
        WHERE variant_id = :variantId
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """;

    // UC-07, RNF-20.4: conditional, so the stock never leaves its range even if the row was not locked.
    private static final String APPLY_MOVEMENT = """
        UPDATE stock_level
        SET on_hand = on_hand + :quantity, updated_at = :updatedAt
        WHERE variant_id = :variantId AND on_hand + :quantity BETWEEN 0 AND :maxOnHand
        """;

    private static final String INSERT_MOVEMENT = """
        INSERT INTO stock_movement (id, variant_id, type, quantity, note, user_id, adjustment_key, created_at)
        VALUES (:id, :variantId, :type, :quantity, :note, :userId, :adjustmentKey, :createdAt)
        """;

    private static final RowMapper<StockMovement> MOVEMENT = JdbcStockLevelStore::movement;

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

    @Override
    public Optional<StockLevel> lockByVariantId(VariantId variantId) {
        return jdbc.query(LOCK_LEVEL, new MapSqlParameterSource("variantId", variantId.value()),
            (row, n) -> StockLevel.of(variantId, row.getInt("on_hand"), row.getBoolean("has_movements"),
                instant(row, "updated_at"))).stream().findFirst();
    }

    @Override
    public Optional<StockMovement> findAdjustment(VariantId variantId, AdjustmentKey key) {
        var params = new MapSqlParameterSource("variantId", variantId.value()).addValue("adjustmentKey", key.value());
        return jdbc.query(SELECT_ADJUSTMENT, params, MOVEMENT).stream().findFirst();
    }

    @Override
    public void save(StockLevel level) {
        for (StockMovement movement : level.newMovements()) {
            var change = new MapSqlParameterSource("variantId", level.variantId().value())
                .addValue("quantity", movement.quantity())
                .addValue("updatedAt", movement.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("maxOnHand", StockLevel.MAX_ON_HAND);
            if (jdbc.update(APPLY_MOVEMENT, change) != 1) {
                throw new IllegalStateException("the stock of variant " + level.variantId() + " cannot take a movement"
                    + " of " + movement.quantity());
            }
            jdbc.update(INSERT_MOVEMENT, new MapSqlParameterSource("id", movement.id().value())
                .addValue("variantId", movement.variantId().value())
                .addValue("type", movement.type().name())
                .addValue("quantity", movement.quantity())
                .addValue("note", movement.note().map(MovementNote::value).orElse(null))
                .addValue("userId", movement.userId().map(UserId::value).orElse(null))
                .addValue("adjustmentKey", movement.adjustmentKey().map(AdjustmentKey::value).orElse(null))
                .addValue("createdAt", movement.createdAt().atOffset(ZoneOffset.UTC)));
        }
    }

    @Override
    public OptionalInt onHand(VariantId variantId) {
        return jdbc.queryForList(SELECT_ON_HAND, new MapSqlParameterSource("variantId", variantId.value()),
                Integer.class).stream().mapToInt(Integer::intValue).findFirst();
    }

    @Override
    public List<StockMovement> movements(VariantId variantId, int offset, int limit) {
        var params = new MapSqlParameterSource("variantId", variantId.value())
            .addValue("offset", offset)
            .addValue("limit", limit);
        return jdbc.query(SELECT_HISTORY, params, MOVEMENT);
    }

    private static StockMovement movement(ResultSet row, int rowNumber) throws SQLException {
        return new StockMovement(
            MovementId.of(row.getObject("id", UUID.class)),
            VariantId.of(row.getObject("variant_id", UUID.class)),
            MovementType.valueOf(row.getString("type")),
            row.getInt("quantity"),
            Optional.ofNullable(row.getString("note")).map(MovementNote::new),
            Optional.ofNullable(row.getObject("user_id", UUID.class)).map(UserId::of),
            Optional.ofNullable(row.getObject("adjustment_key", UUID.class)).map(AdjustmentKey::of),
            instant(row, "created_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }
}
