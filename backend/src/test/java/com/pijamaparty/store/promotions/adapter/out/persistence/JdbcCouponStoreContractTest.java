package com.pijamaparty.store.promotions.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.promotions.application.CouponStoreContractTest;
import com.pijamaparty.store.promotions.application.CouponSummaries;
import com.pijamaparty.store.promotions.application.CouponUses;
import com.pijamaparty.store.promotions.application.CouponView;
import com.pijamaparty.store.promotions.application.LoadCoupon;
import com.pijamaparty.store.promotions.application.RemoveStoredCoupon;
import com.pijamaparty.store.promotions.application.SaveCoupon;
import com.pijamaparty.store.promotions.application.SaveCouponRedemption;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction. Here every call of the contract is its own committed transaction, and
 * the last test checks that a violated constraint leaves the caller's transaction usable and committable, as for
 * categories (T-E1-03). Redemptions are written as the owner: no port records them before E3.
 */
@ApiTest
class JdbcCouponStoreContractTest extends CouponStoreContractTest<JdbcCouponStoreContractTest.InTransaction> {

    private final JdbcCouponStore adapter;
    private final TransactionTemplate transactions;
    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcCouponStoreContractTest(JdbcCouponStore adapter, PlatformTransactionManager transactionManager,
                                PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.inTransaction = new InTransaction(adapter, transactions);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
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
        owner.execute("truncate coupon_scope, coupon_redemption, coupon");
    }

    @Test
    void givenViolatedConstraints_whenTheSameTransactionGoesOn_thenItsOtherWritesAreCommitted() {
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        Coupon used = coupon("VERANO10");
        inTransaction.add(used, now);
        givenRedemptions(used.id(), 1);
        Coupon other = coupon("INVIERNO");

        transactions.executeWithoutResult(status -> {
            assertThat(adapter.add(coupon("VERANO10"), now)).isEqualTo(SaveCoupon.Outcome.CODE_ALREADY_USED);
            assertThat(adapter.remove(used.id())).isEqualTo(RemoveStoredCoupon.Outcome.HAS_USES);
            assertThat(adapter.add(other, now)).isEqualTo(SaveCoupon.Outcome.SAVED);
        });

        assertThat(inTransaction.findById(other.id())).isPresent();
        assertThat(inTransaction.findById(used.id())).isPresent();
    }

    private static Coupon coupon(String code) {
        return Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode(code), CouponType.PERCENTAGE, 10,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcCouponStore adapter, TransactionTemplate transactions)
        implements LoadCoupon, CouponUses, CouponSummaries, SaveCoupon, RemoveStoredCoupon, SaveCouponRedemption {

        @Override
        public Optional<Coupon> findByCode(CouponCode code) {
            return transactions.execute(status -> adapter.findByCode(code));
        }

        @Override
        public Optional<Coupon> findById(CouponId id) {
            return transactions.execute(status -> adapter.findById(id));
        }

        @Override
        public long count(CouponId id) {
            Long uses = transactions.execute(status -> adapter.count(id));
            return uses == null ? 0 : uses;
        }

        @Override
        public List<CouponView> listNewestFirst() {
            return transactions.execute(status -> adapter.listNewestFirst());
        }

        @Override
        public SaveCoupon.Outcome add(Coupon coupon, Instant at) {
            return transactions.execute(status -> adapter.add(coupon, at));
        }

        @Override
        public SaveCoupon.Outcome update(Coupon coupon, Instant at) {
            return transactions.execute(status -> adapter.update(coupon, at));
        }

        @Override
        public RemoveStoredCoupon.Outcome remove(CouponId id) {
            return transactions.execute(status -> adapter.remove(id));
        }

        @Override
        public boolean redeem(CouponId coupon, UUID orderId, Instant at) {
            return Boolean.TRUE.equals(transactions.execute(status -> adapter.redeem(coupon, orderId, at)));
        }
    }
}
