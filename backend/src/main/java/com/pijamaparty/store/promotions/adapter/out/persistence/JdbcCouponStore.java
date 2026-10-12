package com.pijamaparty.store.promotions.adapter.out.persistence;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.Savepoints;
import com.pijamaparty.store.promotions.application.CouponSummaries;
import com.pijamaparty.store.promotions.application.CouponUses;
import com.pijamaparty.store.promotions.application.CouponView;
import com.pijamaparty.store.promotions.application.LoadCoupon;
import com.pijamaparty.store.promotions.application.RemoveStoredCoupon;
import com.pijamaparty.store.promotions.application.SaveCoupon;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Coupons and their uses in tables {@code coupon} and {@code coupon_redemption} (V13), in the caller's transaction.
 * The database enforces the unique code and keeps coupons with uses (foreign key
 * {@code coupon_redemption_coupon_fk}); each write that may break one of them runs inside a savepoint, so the
 * violation becomes an outcome and the caller's transaction stays usable.
 */
@Repository
class JdbcCouponStore implements LoadCoupon, CouponUses, CouponSummaries, SaveCoupon, RemoveStoredCoupon {

    private static final String SELECT_COUPON = """
        SELECT id, code, type, discount_value, valid_from, valid_until, max_uses, minimum_purchase_cents, active
        FROM coupon
        """;

    private static final String BY_CODE = SELECT_COUPON + """
        WHERE code = :code
        """;

    private static final String BY_ID = SELECT_COUPON + """
        WHERE id = :id
        """;

    private static final String COUNT_USES = """
        SELECT count(*)
        FROM coupon_redemption
        WHERE coupon_id = :id
        """;

    private static final String LIST_WITH_USES = """
        SELECT c.id, c.code, c.type, c.discount_value, c.valid_from, c.valid_until, c.max_uses,
               c.minimum_purchase_cents, c.active,
               (SELECT count(*) FROM coupon_redemption r WHERE r.coupon_id = c.id) AS uses
        FROM coupon c
        ORDER BY c.created_at DESC, c.id DESC
        """;

    private static final String INSERT = """
        INSERT INTO coupon (id, code, type, discount_value, valid_from, valid_until, max_uses,
                            minimum_purchase_cents, active, created_at, updated_at)
        VALUES (:id, :code, :type, :discountValue, :validFrom, :validUntil, :maxUses,
                :minimumPurchaseCents, :active, :at, :at)
        """;

    // A plain UPDATE: a coupon deleted since it was read is not brought back.
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

    private static final String DELETE = """
        DELETE FROM coupon
        WHERE id = :id
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Savepoints savepoints;

    JdbcCouponStore(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.savepoints = new Savepoints(dataSource);
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return jdbc.query(BY_CODE, new MapSqlParameterSource("code", code.value()), JdbcCouponStore::coupon)
            .stream().findFirst();
    }

    @Override
    public Optional<Coupon> findById(CouponId id) {
        return jdbc.query(BY_ID, new MapSqlParameterSource("id", id.value()), JdbcCouponStore::coupon)
            .stream().findFirst();
    }

    @Override
    public long count(CouponId id) {
        Long uses = jdbc.queryForObject(COUNT_USES, new MapSqlParameterSource("id", id.value()), Long.class);
        return uses == null ? 0 : uses;
    }

    @Override
    public List<CouponView> listNewestFirst() {
        return jdbc.query(LIST_WITH_USES,
            (row, rowNumber) -> CouponView.of(coupon(row, rowNumber), row.getLong("uses")));
    }

    @Override
    public SaveCoupon.Outcome add(Coupon coupon, Instant at) {
        // Ids are new UUIDs, so the only unique constraint a new row can break is the code's.
        return write(INSERT, parametersOf(coupon, at), DuplicateKeyException.class::isInstance).isPresent()
            ? SaveCoupon.Outcome.SAVED : SaveCoupon.Outcome.CODE_ALREADY_USED;
    }

    @Override
    public SaveCoupon.Outcome update(Coupon coupon, Instant at) {
        // The id does not change, so the only unique constraint an update can break is the code's.
        OptionalInt updated = write(UPDATE, parametersOf(coupon, at), DuplicateKeyException.class::isInstance);
        if (updated.isEmpty()) {
            return SaveCoupon.Outcome.CODE_ALREADY_USED;
        }
        return updated.getAsInt() == 0 ? SaveCoupon.Outcome.NOT_FOUND : SaveCoupon.Outcome.SAVED;
    }

    @Override
    public RemoveStoredCoupon.Outcome remove(CouponId id) {
        // The only foreign key that points to coupon is coupon_redemption_coupon_fk.
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
            .addValue("active", coupon.active())
            .addValue("at", at.atOffset(ZoneOffset.UTC));
    }

    private static Coupon coupon(ResultSet row, int rowNumber) throws SQLException {
        return Coupon.of(
            CouponId.of(row.getObject("id", UUID.class)),
            new CouponCode(row.getString("code")),
            CouponType.valueOf(row.getString("type")),
            row.getLong("discount_value"),
            Optional.ofNullable(row.getObject("valid_from", LocalDate.class)),
            Optional.ofNullable(row.getObject("valid_until", LocalDate.class)),
            Optional.ofNullable(row.getObject("max_uses", Integer.class)),
            Optional.ofNullable(row.getObject("minimum_purchase_cents", Long.class)).map(Money::of),
            row.getBoolean("active"));
    }
}
