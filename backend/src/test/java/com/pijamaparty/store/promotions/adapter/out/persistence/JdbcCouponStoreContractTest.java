package com.pijamaparty.store.promotions.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.promotions.application.CouponStoreContractTest;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.sql.Types;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Read back from PostgreSQL; the rows are written as the owner, since no port writes coupons before T-E2-06. */
@ApiTest
class JdbcCouponStoreContractTest extends CouponStoreContractTest<JdbcCouponStore> {

    private final JdbcCouponStore adapter;
    private final JdbcTemplate owner;

    JdbcCouponStoreContractTest(JdbcCouponStore adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected JdbcCouponStore createSubject() {
        return adapter;
    }

    @Override
    protected void givenCoupon(Coupon coupon) {
        owner.update("""
            insert into coupon (id, code, type, discount_value, valid_from, valid_until, max_uses,
                                minimum_purchase_cents, active, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, coupon.id().value(), coupon.code().value(), coupon.type().name(), coupon.discountValue(),
            new SqlParameterValue(Types.DATE, coupon.validFrom().orElse(null)),
            new SqlParameterValue(Types.DATE, coupon.validUntil().orElse(null)),
            new SqlParameterValue(Types.INTEGER, coupon.maxUses().orElse(null)),
            new SqlParameterValue(Types.BIGINT, coupon.minimumPurchase().map(Money::cents).orElse(null)),
            coupon.active());
    }

    @Override
    protected void givenRedemptions(CouponId id, int orders) {
        for (int i = 0; i < orders; i++) {
            owner.update("insert into coupon_redemption (coupon_id, order_id, redeemed_at) values (?, ?, now())",
                id.value(), UUID.randomUUID());
        }
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate coupon_redemption, coupon");
    }
}
