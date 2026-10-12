package com.pijamaparty.store.promotions.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** T-E2-01: the promotions schema (V13) enforces the UC-10 and UC-11 rules, connected as the application role. */
@ApiTest
class PromotionsSchemaTest {

    private static final String INSERT_COUPON = """
        insert into coupon (id, code, type, discount_value, valid_from, valid_until, max_uses, minimum_purchase_cents,
                            active, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, true, now(), now())
        """;
    private static final String REDEEM = """
        insert into coupon_redemption (coupon_id, order_id, redeemed_at) values (?, ?, now()) on conflict do nothing
        """;

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    PromotionsSchemaTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate coupon_redemption, coupon");
    }

    @Test
    void givenAPercentageAndAFixedAmountCoupon_whenStored_thenBothAreAccepted() {
        coupon("VERANO-10", "PERCENTAGE", 10);
        app.update(INSERT_COUPON, UUID.randomUUID(), "MENOS2000", "FIXED_AMOUNT", 200_000L,
            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), 50, 2_000_000L);

        assertThat(app.queryForObject("select count(*) from coupon", Integer.class)).isEqualTo(2);
    }

    @Test
    void givenAnExistingCode_whenAnotherCouponWithTheSameCodeIsCreated_thenItIsRejected() {
        coupon("VERANO10", "PERCENTAGE", 10);

        assertThatThrownBy(() -> coupon("VERANO10", "FIXED_AMOUNT", 100_000))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("coupon_code_uk");
    }

    @Test
    void givenACodeNotNormalized_whenStored_thenItIsRejected() {
        for (String code : new String[] {"verano10", "AB", "A".repeat(21), "VERANO 10", "VERANO_10"}) {
            assertThatThrownBy(() -> coupon(code, "PERCENTAGE", 10)).as(code)
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_code_format_ck");
        }
    }

    @Test
    void givenAPercentageOutside1To100OrANonPositiveAmount_whenStored_thenItIsRejected() {
        assertThatThrownBy(() -> coupon("MAS-DE-100", "PERCENTAGE", 101))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_discount_value_ck");
        assertThatThrownBy(() -> coupon("CERO", "PERCENTAGE", 0))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_discount_value_ck");
        assertThatThrownBy(() -> coupon("GRATIS", "FIXED_AMOUNT", 0))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_discount_value_ck");
        assertThatThrownBy(() -> coupon("OTRO", "SHIPPING", 10))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_type_ck");
    }

    @Test
    void givenAStartAfterTheEnd_whenStored_thenItIsRejected() {
        assertThatThrownBy(() -> app.update(INSERT_COUPON, UUID.randomUUID(), "AL-REVES", "PERCENTAGE", 10L,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1), null, null))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_validity_ck");
    }

    @Test
    void givenZeroMaximumUsesOrMinimumPurchase_whenStored_thenItIsRejected() {
        assertThatThrownBy(() -> app.update(INSERT_COUPON, UUID.randomUUID(), "SIN-USOS", "PERCENTAGE", 10L, null,
                null, 0, null))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_max_uses_ck");
        assertThatThrownBy(() -> app.update(INSERT_COUPON, UUID.randomUUID(), "MINIMO-0", "PERCENTAGE", 10L, null,
                null, null, 0L))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_minimum_purchase_ck");
    }

    @Test
    void givenAConfirmedOrder_whenItsCouponUseIsRecordedTwice_thenItCountsOnce() {
        var coupon = coupon("VERANO10", "PERCENTAGE", 10);
        var order = UUID.randomUUID();

        app.update(REDEEM, coupon, order);
        app.update(REDEEM, coupon, order);

        assertThat(app.queryForObject("select count(*) from coupon_redemption where coupon_id = ?", Integer.class,
            coupon)).isOne();
    }

    @Test
    void givenACouponWithRecordedUses_whenDeleted_thenItIsRejected() {
        var coupon = coupon("VERANO10", "PERCENTAGE", 10);
        app.update(REDEEM, coupon, UUID.randomUUID());

        assertThatThrownBy(() -> app.update("delete from coupon where id = ?", coupon))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("coupon_redemption_coupon_fk");
    }

    @Test
    void givenACouponWithoutUses_whenDeleted_thenItIsAllowed() {
        var coupon = coupon("VERANO10", "PERCENTAGE", 10);

        assertThat(app.update("delete from coupon where id = ?", coupon)).isOne();
    }

    private UUID coupon(String code, String type, long value) {
        var id = UUID.randomUUID();
        app.update(INSERT_COUPON, id, code, type, value, null, null, null, null);
        return id;
    }
}
