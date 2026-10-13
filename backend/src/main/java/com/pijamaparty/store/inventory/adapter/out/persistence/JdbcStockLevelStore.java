package com.pijamaparty.store.inventory.adapter.out.persistence;

import com.pijamaparty.store.inventory.application.AddStockLevels;
import com.pijamaparty.store.inventory.application.AvailableStock;
import com.pijamaparty.store.inventory.application.LoadStockLevels;
import com.pijamaparty.store.inventory.application.OrderReservations;
import com.pijamaparty.store.inventory.application.ReserveStock;
import com.pijamaparty.store.inventory.application.SaveStockLevels;
import com.pijamaparty.store.inventory.application.SettleReservations;
import com.pijamaparty.store.inventory.application.StockHistory;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.Savepoints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Stock levels and movements in tables {@code stock_level} and {@code stock_movement} (V4, V8, V17), and the available
 * stock in the view {@code v_variant_availability} (V9, V17), in the caller's transaction.
 */
@Repository
class JdbcStockLevelStore implements AddStockLevels, LoadStockLevels, SaveStockLevels, StockHistory, AvailableStock,
    ReserveStock, OrderReservations, SettleReservations {

    // Idempotent: a variant opened twice keeps its stock.
    private static final String INSERT_EMPTY = """
        INSERT INTO stock_level (variant_id, on_hand, updated_at)
        VALUES (:variantId, 0, :updatedAt)
        ON CONFLICT (variant_id) DO NOTHING
        """;

    private static final String LOCK_LEVEL = """
        SELECT l.on_hand, l.reserved, l.updated_at,
               EXISTS (SELECT 1 FROM stock_movement m WHERE m.variant_id = l.variant_id) AS has_movements
        FROM stock_level l
        WHERE l.variant_id = :variantId
        FOR UPDATE OF l
        """;

    private static final String SELECT_ON_HAND = """
        SELECT on_hand FROM stock_level WHERE variant_id = :variantId
        """;

    private static final String MOVEMENT_COLUMNS = """
        SELECT id, variant_id, type, quantity, note, user_id, adjustment_key, order_id, created_at
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

    // UC-07, UC-16, RNF-20.4: conditional, so the stock never leaves its range, nor goes below the reserved units, even
    // if the row was not locked.
    private static final String APPLY_MOVEMENT = """
        UPDATE stock_level
        SET on_hand = on_hand + :quantity, updated_at = :updatedAt
        WHERE variant_id = :variantId AND on_hand + :quantity BETWEEN reserved AND :maxOnHand
        """;

    private static final String INSERT_MOVEMENT = """
        INSERT INTO stock_movement (id, variant_id, type, quantity, note, user_id, adjustment_key, order_id,
                                    created_at)
        VALUES (:id, :variantId, :type, :quantity, :note, :userId, :adjustmentKey, :orderId, :createdAt)
        """;

    // The view holds the formula of the available stock (on hand − reserved), so it is written only once.
    private static final String SELECT_AVAILABLE = """
        SELECT variant_id, available
        FROM v_variant_availability
        WHERE variant_id = ANY (CAST(:variantIds AS uuid[]))
        """;

    // UC-16: a reservation is a conditional update; no row means not enough available stock (or no stock level).
    private static final String RESERVE = """
        UPDATE stock_level
        SET reserved = reserved + :units, updated_at = :updatedAt
        WHERE variant_id = :variantId AND on_hand - reserved >= :units
        """;

    private static final String SELECT_ORDER_HAS_RESERVATIONS = """
        SELECT EXISTS (SELECT 1 FROM stock_movement WHERE order_id = :orderId AND type = 'RESERVATION')
        """;

    private static final String SELECT_OUTSTANDING = MOVEMENT_COLUMNS + """
        WHERE order_id = :orderId AND type = 'RESERVATION'
          AND NOT EXISTS (SELECT 1 FROM stock_movement s
                          WHERE s.order_id = stock_movement.order_id AND s.variant_id = stock_movement.variant_id
                            AND s.type IN ('DEDUCTION', 'RELEASE'))
        ORDER BY variant_id
        """;

    // Settlements of one variant run one after the other, so the check below sees the settlement that went first.
    private static final String LOCK_FOR_SETTLEMENT = """
        SELECT variant_id FROM stock_level WHERE variant_id = :variantId FOR UPDATE
        """;

    // RNF-20.4: a reservation is deducted or released once; the unique index is the last line of defense.
    private static final String INSERT_SETTLEMENT = """
        INSERT INTO stock_movement (id, variant_id, type, quantity, order_id, created_at)
        SELECT :id, :variantId, :type, :quantity, :orderId, :createdAt
        WHERE NOT EXISTS (SELECT 1 FROM stock_movement
                          WHERE order_id = :orderId AND variant_id = :variantId
                            AND type IN ('DEDUCTION', 'RELEASE'))
        ON CONFLICT (order_id, variant_id, type) WHERE order_id IS NOT NULL DO NOTHING
        """;

    // A deduction takes the units from on hand and reserved; a release only from reserved.
    private static final String APPLY_SETTLEMENT = """
        UPDATE stock_level
        SET on_hand = on_hand - :taken, reserved = reserved - :units, updated_at = :updatedAt
        WHERE variant_id = :variantId
        """;

    private static final RowMapper<StockMovement> MOVEMENT = JdbcStockLevelStore::movement;

    private final NamedParameterJdbcTemplate jdbc;
    private final Savepoints savepoints;

    JdbcStockLevelStore(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.savepoints = new Savepoints(dataSource);
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
            (row, n) -> StockLevel.of(variantId, row.getInt("on_hand"), row.getInt("reserved"),
                row.getBoolean("has_movements"), instant(row, "updated_at"))).stream().findFirst();
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
            insertMovement(movement);
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

    @Override
    public Map<VariantId, Integer> availableOf(Collection<VariantId> variantIds) {
        UUID[] ids = variantIds.stream().map(VariantId::value).distinct().toArray(UUID[]::new);
        return jdbc.query(SELECT_AVAILABLE, new MapSqlParameterSource().addValue("variantIds", ids, Types.ARRAY),
                (row, n) -> Map.entry(VariantId.of(row.getObject("variant_id", UUID.class)), row.getInt("available")))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Outcome reserveAll(List<StockMovement> reservations) {
        return savepoints.runKeepingIf(() -> {
            for (StockMovement reservation : reservations) {
                requireType(reservation, MovementType.RESERVATION);
                var change = new MapSqlParameterSource("variantId", reservation.variantId().value())
                    .addValue("units", reservation.units())
                    .addValue("updatedAt", reservation.createdAt().atOffset(ZoneOffset.UTC));
                if (jdbc.update(RESERVE, change) != 1) {
                    VariantId variantId = reservation.variantId();
                    return new Outcome.Short(variantId, availableOf(List.of(variantId)).getOrDefault(variantId, 0));
                }
                insertMovement(reservation);
            }
            return new Outcome.Reserved();
        }, Outcome.Reserved.class::isInstance);
    }

    @Override
    public boolean hasAny(OrderRef order) {
        return Boolean.TRUE.equals(jdbc.queryForObject(SELECT_ORDER_HAS_RESERVATIONS,
            new MapSqlParameterSource("orderId", order.value()), Boolean.class));
    }

    @Override
    public List<StockMovement> outstanding(OrderRef order) {
        return jdbc.query(SELECT_OUTSTANDING, new MapSqlParameterSource("orderId", order.value()), MOVEMENT);
    }

    @Override
    public void settle(List<StockMovement> settlements) {
        for (StockMovement settlement : settlements) {
            if (settlement.type() != MovementType.DEDUCTION && settlement.type() != MovementType.RELEASE) {
                throw new IllegalArgumentException("not a deduction or a release: " + settlement.type());
            }
            var variant = new MapSqlParameterSource("variantId", settlement.variantId().value());
            jdbc.queryForList(LOCK_FOR_SETTLEMENT, variant, UUID.class);
            int inserted = jdbc.update(INSERT_SETTLEMENT, new MapSqlParameterSource("id", settlement.id().value())
                .addValue("variantId", settlement.variantId().value())
                .addValue("type", settlement.type().name())
                .addValue("quantity", settlement.quantity())
                .addValue("orderId", settlement.order().orElseThrow().value())
                .addValue("createdAt", settlement.createdAt().atOffset(ZoneOffset.UTC)));
            if (inserted == 1) {
                jdbc.update(APPLY_SETTLEMENT, variant
                    .addValue("taken", settlement.type() == MovementType.DEDUCTION ? settlement.units() : 0)
                    .addValue("units", settlement.units())
                    .addValue("updatedAt", settlement.createdAt().atOffset(ZoneOffset.UTC)));
            }
        }
    }

    private void insertMovement(StockMovement movement) {
        jdbc.update(INSERT_MOVEMENT, new MapSqlParameterSource("id", movement.id().value())
            .addValue("variantId", movement.variantId().value())
            .addValue("type", movement.type().name())
            .addValue("quantity", movement.quantity())
            .addValue("note", movement.note().map(MovementNote::value).orElse(null))
            .addValue("userId", movement.userId().map(UserId::value).orElse(null))
            .addValue("adjustmentKey", movement.adjustmentKey().map(AdjustmentKey::value).orElse(null))
            .addValue("orderId", movement.order().map(OrderRef::value).orElse(null))
            .addValue("createdAt", movement.createdAt().atOffset(ZoneOffset.UTC)));
    }

    private static void requireType(StockMovement movement, MovementType type) {
        if (movement.type() != type) {
            throw new IllegalArgumentException("not a " + type + ": " + movement.type());
        }
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
            Optional.ofNullable(row.getObject("order_id", UUID.class)).map(OrderRef::of),
            instant(row, "created_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }
}
