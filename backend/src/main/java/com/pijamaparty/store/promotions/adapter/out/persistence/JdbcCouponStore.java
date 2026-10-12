package com.pijamaparty.store.promotions.adapter.out.persistence;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.application.CouponUses;
import com.pijamaparty.store.promotions.application.LoadCoupon;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Coupons and their uses in tables {@code coupon} and {@code coupon_redemption} (V13), in the caller's transaction. */
@Repository
class JdbcCouponStore implements LoadCoupon, CouponUses {

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

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCouponStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
