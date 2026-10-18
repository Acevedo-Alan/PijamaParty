package com.pijamaparty.store.promotions.adapter.out.persistence;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.Savepoints;
import com.pijamaparty.store.promotions.application.CouponSummaries;
import com.pijamaparty.store.promotions.application.CouponUses;
import com.pijamaparty.store.promotions.application.CouponView;
import com.pijamaparty.store.promotions.application.LoadAutomaticCoupons;
import com.pijamaparty.store.promotions.application.LoadCoupon;
import com.pijamaparty.store.promotions.application.RemoveStoredCoupon;
import com.pijamaparty.store.promotions.application.SaveCoupon;
import com.pijamaparty.store.promotions.application.SaveCouponRedemption;
import com.pijamaparty.store.promotions.domain.CountedCoupon;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponScope;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Coupons, their scopes and their uses in tables {@code coupon}, {@code coupon_scope} (V37) and
 * {@code coupon_redemption} (V13), in the caller's transaction. The database enforces the unique code and keeps
 * coupons with uses (foreign key {@code coupon_redemption_coupon_fk}); each write that may break one of them runs inside
 * a savepoint, so the violation becomes an outcome and the caller's transaction stays usable.
 */
@Repository
class JdbcCouponStore implements LoadCoupon, LoadAutomaticCoupons, CouponUses, CouponSummaries, SaveCoupon,
    RemoveStoredCoupon, SaveCouponRedemption {

    private static final String SELECT_COUPON = """
        SELECT c.id, c.code, c.type, c.discount_value, c.valid_from, c.valid_until, c.max_uses,
               c.minimum_purchase_cents, c.automatic, c.active
        FROM coupon c
        """;

    private static final String SELECT_WITH_USES = """
        SELECT c.id, c.code, c.type, c.discount_value, c.valid_from, c.valid_until, c.max_uses,
               c.minimum_purchase_cents, c.automatic, c.active,
               (SELECT count(*) FROM coupon_redemption r WHERE r.coupon_id = c.id) AS uses
        FROM coupon c
        """;

    private static final String BY_CODE = SELECT_COUPON + """
        WHERE c.code = :code
        """;

    private static final String BY_ID = SELECT_COUPON + """
        WHERE c.id = :id
        """;

    private static final String COUNT_USES = """
        SELECT count(*)
        FROM coupon_redemption
        WHERE coupon_id = :id
        """;

    private static final String LIST_WITH_USES = SELECT_WITH_USES + """
        ORDER BY c.created_at DESC, c.id DESC
        """;

    // UC-23: the coupons a cart is priced with, oldest first for the tie between automatic coupons.
    private static final String ACTIVE_AUTOMATIC_WITH_USES = SELECT_WITH_USES + """
        WHERE c.automatic AND c.active
        ORDER BY c.created_at, c.id
        """;

    private static final String SCOPES = """
        SELECT coupon_id, target_type, target_id
        FROM coupon_scope
        WHERE coupon_id = ANY (CAST(:couponIds AS uuid[]))
        """;

    private static final String INSERT = """
        INSERT INTO coupon (id, code, type, discount_value, valid_from, valid_until, max_uses,
                            minimum_purchase_cents, automatic, active, created_at, updated_at)
        VALUES (:id, :code, :type, :discountValue, :validFrom, :validUntil, :maxUses,
                :minimumPurchaseCents, :automatic, :active, :at, :at)
        """;

    // A plain UPDATE: a coupon deleted since it was read is not brought back. Whether it is automatic never changes
    // (coupon_automatic_update_guard, V37).
    private static final String UPDATE = """
        UPDATE coupon
        SET code = :code,
            type = :type,
            discount_value = :discountValue,
            valid_from = :validFrom,
            valid_until = :validUntil,
            max_uses = :maxUses,
            minimum_purchase_cents = :minimumPurchaseCents,
            active = :active,
            updated_at = :at
        WHERE id = :id
        """;

    private static final String DELETE_SCOPE = """
        DELETE FROM coupon_scope
        WHERE coupon_id = :id
        """;

    private static final String INSERT_SCOPE = """
        INSERT INTO coupon_scope (coupon_id, target_type, target_id)
        SELECT :id, t.target_type, t.target_id
        FROM unnest(CAST(:targetTypes AS text[]), CAST(:targetIds AS uuid[])) AS t (target_type, target_id)
        """;

    private static final String DELETE = """
        DELETE FROM coupon
        WHERE id = :id
        """;

    // UC-15: once per coupon and order (coupon_redemption_pk); a coupon deleted meanwhile gets no use.
    private static final String REDEEM = """
        INSERT INTO coupon_redemption (coupon_id, order_id, redeemed_at)
        SELECT id, :orderId, :at FROM coupon WHERE id = :couponId
        ON CONFLICT (coupon_id, order_id) DO NOTHING
        """;

    private static final String PRODUCT = "PRODUCT";
    private static final String CATEGORY = "CATEGORY";

    private final NamedParameterJdbcTemplate jdbc;
    private final Savepoints savepoints;

    JdbcCouponStore(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.savepoints = new Savepoints(dataSource);
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return withScopes(jdbc.query(BY_CODE, new MapSqlParameterSource("code", code.value()), JdbcCouponStore::row))
            .stream().findFirst();
    }

    @Override
    public Optional<Coupon> findById(CouponId id) {
        return withScopes(jdbc.query(BY_ID, new MapSqlParameterSource("id", id.value()), JdbcCouponStore::row))
            .stream().findFirst();
    }

    @Override
    public List<CountedCoupon> activeOldestFirst() {
        List<CountedRow> rows = jdbc.query(ACTIVE_AUTOMATIC_WITH_USES, JdbcCouponStore::countedRow);
        Map<UUID, CouponScope> scopes = scopesOf(rows.stream().map(CountedRow::row));
        return rows.stream().map(counted -> new CountedCoupon(counted.row().with(scopes), counted.uses())).toList();
    }

    @Override
    public long count(CouponId id) {
        Long uses = jdbc.queryForObject(COUNT_USES, new MapSqlParameterSource("id", id.value()), Long.class);
        return uses == null ? 0 : uses;
    }

    @Override
    public boolean redeem(CouponId coupon, UUID orderId, Instant at) {
        return jdbc.update(REDEEM, new MapSqlParameterSource("couponId", coupon.value())
            .addValue("orderId", orderId)
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    public List<CouponView> listNewestFirst() {
        List<CountedRow> rows = jdbc.query(LIST_WITH_USES, JdbcCouponStore::countedRow);
        Map<UUID, CouponScope> scopes = scopesOf(rows.stream().map(CountedRow::row));
        return rows.stream().map(counted -> CouponView.of(counted.row().with(scopes), counted.uses())).toList();
    }

    @Override
    public SaveCoupon.Outcome add(Coupon coupon, Instant at) {
        // Ids are new UUIDs, so the only unique constraint a new row can break is the code's.
        if (write(INSERT, parametersOf(coupon, at), DuplicateKeyException.class::isInstance).isEmpty()) {
            return SaveCoupon.Outcome.CODE_ALREADY_USED;
        }
        insertScope(coupon);
        return SaveCoupon.Outcome.SAVED;
    }

    @Override
    public SaveCoupon.Outcome update(Coupon coupon, Instant at) {
        // The id does not change, so the only unique constraint an update can break is the code's.
        OptionalInt updated = write(UPDATE, parametersOf(coupon, at), DuplicateKeyException.class::isInstance);
        if (updated.isEmpty()) {
            return SaveCoupon.Outcome.CODE_ALREADY_USED;
        }
        if (updated.getAsInt() == 0) {
            return SaveCoupon.Outcome.NOT_FOUND;
        }
        // Only an automatic coupon has a scope (coupon_scope_automatic_guard, V37).
        if (coupon.automatic()) {
            jdbc.update(DELETE_SCOPE, new MapSqlParameterSource("id", coupon.id().value()));
            insertScope(coupon);
        }
        return SaveCoupon.Outcome.SAVED;
    }

    @Override
    public RemoveStoredCoupon.Outcome remove(CouponId id) {
        // The only foreign key that points to coupon without deleting with it is coupon_redemption_coupon_fk.
        OptionalInt deleted = write(DELETE, new MapSqlParameterSource("id", id.value()),
            Savepoints::isForeignKeyViolation);
        if (deleted.isEmpty()) {
            return RemoveStoredCoupon.Outcome.HAS_USES;
        }
        return deleted.getAsInt() == 0 ? RemoveStoredCoupon.Outcome.NOT_FOUND : RemoveStoredCoupon.Outcome.REMOVED;
    }

    /**
     * Runs the statement inside a savepoint of the caller's transaction. Returns the affected rows, or empty when it
     * broke an {@code expected} constraint: then only the statement is rolled back. Any other failure propagates.
     */
    private OptionalInt write(String sql, MapSqlParameterSource parameters,
                              Predicate<DataIntegrityViolationException> expected) {
        return savepoints.run(() -> OptionalInt.of(jdbc.update(sql, parameters)),
            violation -> expected.test(violation) ? Optional.of(OptionalInt.empty()) : Optional.empty());
    }

    private void insertScope(Coupon coupon) {
        CouponScope scope = coupon.scope();
        if (scope.isWholeCart()) {
            return;
        }
        List<String> types = Stream.concat(scope.productIds().stream().map(id -> PRODUCT),
            scope.categoryIds().stream().map(id -> CATEGORY)).toList();
        UUID[] ids = Stream.concat(scope.productIds().stream(), scope.categoryIds().stream()).toArray(UUID[]::new);
        jdbc.update(INSERT_SCOPE, new MapSqlParameterSource("id", coupon.id().value())
            .addValue("targetTypes", types.toArray(String[]::new), Types.ARRAY)
            .addValue("targetIds", ids, Types.ARRAY));
    }

    private List<Coupon> withScopes(List<CouponRow> rows) {
        Map<UUID, CouponScope> scopes = scopesOf(rows.stream());
        return rows.stream().map(row -> row.with(scopes)).toList();
    }

    /** The scope of each automatic coupon among {@code rows} that has one. */
    private Map<UUID, CouponScope> scopesOf(Stream<CouponRow> rows) {
        UUID[] automatic = rows.filter(CouponRow::automatic).map(CouponRow::id).toArray(UUID[]::new);
        if (automatic.length == 0) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> products = new HashMap<>();
        Map<UUID, Set<UUID>> categories = new HashMap<>();
        jdbc.query(SCOPES, new MapSqlParameterSource().addValue("couponIds", automatic, Types.ARRAY), row -> {
            Map<UUID, Set<UUID>> targets = PRODUCT.equals(row.getString("target_type")) ? products : categories;
            targets.computeIfAbsent(row.getObject("coupon_id", UUID.class), id -> new HashSet<>())
                .add(row.getObject("target_id", UUID.class));
        });
        Map<UUID, CouponScope> scopes = new HashMap<>();
        Stream.concat(products.keySet().stream(), categories.keySet().stream()).distinct().forEach(id -> scopes.put(id,
            new CouponScope(products.getOrDefault(id, Set.of()), categories.getOrDefault(id, Set.of()))));
        return scopes;
    }

    private static MapSqlParameterSource parametersOf(Coupon coupon, Instant at) {
        return new MapSqlParameterSource()
            .addValue("id", coupon.id().value())
            .addValue("code", coupon.code().value())
            .addValue("type", coupon.type().name())
            .addValue("discountValue", coupon.discountValue())
            .addValue("validFrom", coupon.validFrom().orElse(null), Types.DATE)
            .addValue("validUntil", coupon.validUntil().orElse(null), Types.DATE)
            .addValue("maxUses", coupon.maxUses().orElse(null), Types.INTEGER)
            .addValue("minimumPurchaseCents", coupon.minimumPurchase().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("automatic", coupon.automatic())
            .addValue("active", coupon.active())
            .addValue("at", at.atOffset(ZoneOffset.UTC));
    }

    private static CouponRow row(ResultSet row, int rowNumber) throws SQLException {
        return new CouponRow(
            row.getObject("id", UUID.class),
            new CouponCode(row.getString("code")),
            CouponType.valueOf(row.getString("type")),
            row.getLong("discount_value"),
            Optional.ofNullable(row.getObject("valid_from", LocalDate.class)),
            Optional.ofNullable(row.getObject("valid_until", LocalDate.class)),
            Optional.ofNullable(row.getObject("max_uses", Integer.class)),
            Optional.ofNullable(row.getObject("minimum_purchase_cents", Long.class)).map(Money::of),
            row.getBoolean("automatic"),
            row.getBoolean("active"));
    }

    private static CountedRow countedRow(ResultSet row, int rowNumber) throws SQLException {
        return new CountedRow(row(row, rowNumber), row.getLong("uses"));
    }

    /** A {@code coupon} row, which becomes a {@link Coupon} once its scope is read. */
    private record CouponRow(UUID id, CouponCode code, CouponType type, long discountValue,
                             Optional<LocalDate> validFrom, Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                             Optional<Money> minimumPurchase, boolean automatic, boolean active) {

        /** @param scopes by coupon id; a coupon without an entry discounts the whole cart */
        Coupon with(Map<UUID, CouponScope> scopes) {
            CouponId couponId = CouponId.of(id);
            return automatic
                ? Coupon.automatic(couponId, code, type, discountValue, validFrom, validUntil, maxUses,
                    minimumPurchase, scopes.getOrDefault(id, CouponScope.WHOLE_CART), active)
                : Coupon.of(couponId, code, type, discountValue, validFrom, validUntil, maxUses, minimumPurchase,
                    active);
        }
    }

    private record CountedRow(CouponRow row, long uses) {}
}
