package com.pijamaparty.store.ordering.adapter.out.promotions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.OrderCouponRedemptions;
import com.pijamaparty.store.ordering.application.OrderCouponRedemptionsContractTest;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the public API of the promotions module to its tables in PostgreSQL, each call in its own transaction. */
@ApiTest
class PromotionsModuleCouponRedemptionsContractTest
    extends OrderCouponRedemptionsContractTest<OrderCouponRedemptions> {

    private final PromotionsModuleCouponRedemptions adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    PromotionsModuleCouponRedemptionsContractTest(PromotionsModuleCouponRedemptions adapter,
                                                  PlatformTransactionManager transactionManager,
                                                  PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OrderCouponRedemptions createSubject() {
        return (couponId, order, at) -> transactions.executeWithoutResult(status ->
            adapter.redeem(couponId, order, at));
    }

    @Override
    protected UUID givenCoupon() {
        UUID coupon = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, active, created_at, updated_at)
            values (?, ?, 'PERCENTAGE', 10, true, now(), now())
            """, coupon, "C-" + coupon.toString().substring(0, 8).toUpperCase());
        return coupon;
    }

    @Override
    protected long usesOf(UUID couponId) {
        Long uses = owner.queryForObject("select count(*) from coupon_redemption where coupon_id = ?", Long.class,
            couponId);
        return uses == null ? 0 : uses;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate coupon_redemption, coupon");
    }

    @Test
    void givenNoTransaction_whenAUseIsRecorded_thenItIsRefused() {
        UUID coupon = givenCoupon();

        assertThatThrownBy(() -> adapter.redeem(coupon, OrderId.of(UUID.randomUUID()), NOW))
            .isInstanceOf(IllegalTransactionStateException.class);
    }
}
