package com.pijamaparty.store.cart.adapter.out.promotions;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.cart.application.CouponsContractTest;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the public API of promotions to its tables in PostgreSQL. */
@ApiTest
class PromotionsCouponsContractTest extends CouponsContractTest<PromotionsCoupons> {

    private final PromotionsCoupons adapter;
    private final JdbcTemplate owner;
    private int automaticCoupons;

    PromotionsCouponsContractTest(PromotionsCoupons adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected PromotionsCoupons createSubject() {
        return adapter;
    }

    @Override
    protected CouponId givenPercentageCoupon(String code, int percent, Money minimum) {
        return coupon(code, percent, minimum, null);
    }

    @Override
    protected CouponId givenExpiredCoupon(String code) {
        return coupon(code, 10, Money.of(1), LocalDate.of(2020, 1, 31));
    }

    @Override
    protected CouponId givenAutomaticCoupon(String code, int percent, Money minimum, Optional<UUID> category) {
        UUID id = UUID.randomUUID();
        // Each one newer than the last: the tie between automatic coupons goes to the oldest.
        owner.update("""
            insert into coupon (id, code, type, discount_value, minimum_purchase_cents, automatic, active, created_at,
                                updated_at)
            values (?, ?, 'PERCENTAGE', ?, ?, true, true, now() + ? * interval '1 second', now())
            """, id, code, percent, minimum.cents(), ++automaticCoupons);
        category.ifPresent(target -> owner.update(
            "insert into coupon_scope (coupon_id, target_type, target_id) values (?, 'CATEGORY', ?)", id, target));
        return CouponId.of(id);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate coupon_scope, coupon_redemption, coupon");
    }

    /** {@code validUntil} null: no last day. */
    private CouponId coupon(String code, int percent, Money minimum, LocalDate validUntil) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, valid_until, minimum_purchase_cents, active,
                                created_at, updated_at)
            values (?, ?, 'PERCENTAGE', ?, ?, ?, true, now(), now())
            """, id, code, percent, new SqlParameterValue(Types.DATE, validUntil), minimum.cents());
        return CouponId.of(id);
    }
}
