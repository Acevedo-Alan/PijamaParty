package com.pijamaparty.store.payments.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-01: the payments schema (V15) enforces the UC-14 and UC-15 rules, and its Row Level Security policies
 * (RNF-27). The full policy matrix is T-E3-21.
 */
@ApiTest
class PaymentsSchemaTest {

    private static final String INSERT_PAYMENT = """
        insert into payment (id, order_id, provider, amount_cents, status, checkout_id, checkout_url,
                             external_payment_id, created_at, updated_at)
        values (?, ?, 'MERCADOPAGO', 2100000, ?, ?, ?, ?, now(), now())
        """;

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    PaymentsSchemaTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                       PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate payment");
    }

    @Test
    void givenThePaymentTable_whenCheckingItsProtection_thenRowLevelSecurityIsEnabledAndForced() {
        var flags = owner.queryForMap(
            "select relrowsecurity, relforcerowsecurity from pg_class where oid = 'payment'::regclass");

        assertThat(flags).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @Test
    void givenTheSystem_whenItCreatesAPaymentAndRecordsItsCheckoutAndApproval_thenTheyAreStored() {
        var payment = UUID.randomUUID();
        rls.as(SYSTEM, jdbc -> jdbc.update(INSERT_PAYMENT, payment, UUID.randomUUID(), "PENDING", null, null, null));

        int updated = rls.as(SYSTEM, jdbc -> jdbc.update("""
            update payment set checkout_id = '123-abc', checkout_url = 'https://www.mercadopago.com.ar/checkout/x',
                               status = 'APPROVED', external_payment_id = '987654', updated_at = now()
            where id = ?
            """, payment));

        assertThat(updated).isOne();
        assertThat(rls.as(ADMIN, this::paymentIds)).containsExactly(payment);
    }

    @Test
    void givenAnOrderWithAPayment_whenASecondPaymentOfTheSameProviderIsStored_thenItIsRejected() {
        var order = UUID.randomUUID();
        owner.update(INSERT_PAYMENT, UUID.randomUUID(), order, "PENDING", null, null, null);

        assertThatThrownBy(() -> owner.update(INSERT_PAYMENT, UUID.randomUUID(), order, "PENDING", null, null, null))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("payment_order_provider_uk");
    }

    @Test
    void givenInconsistentPaymentData_whenStored_thenItIsRejected() {
        rejects("PENDING", "123-abc", null, null, "payment_checkout_ck");
        rejects("PENDING", "123-abc", "http://example.com/checkout", null, "payment_checkout_url_ck");
        rejects("APPROVED", null, null, null, "payment_approved_ck");
        rejects("REFUNDED", null, null, "987654", "payment_status_ck");
    }

    @Test
    void givenAPayment_whenABuyerOrAnActorWithoutContextReadsOrWritesPayments_thenNothingIsReachable() {
        owner.update(INSERT_PAYMENT, UUID.randomUUID(), UUID.randomUUID(), "PENDING", null, null, null);

        assertThat(rls.as(CUSTOMER_A, this::paymentIds)).isEmpty();
        assertThat(rls.as(GUEST, this::paymentIds)).isEmpty();
        assertThat(rls.withoutContext(this::paymentIds)).isEmpty();
        assertThat(rls.<Integer>as(ADMIN, jdbc -> jdbc.update("update payment set status = 'APPROVED'"))).isZero();
        assertThatThrownBy(() -> rls.as(GUEST, jdbc -> jdbc.update(INSERT_PAYMENT, UUID.randomUUID(),
                UUID.randomUUID(), "PENDING", null, null, null)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenAPayment_whenTheSystemTriesToDeleteIt_thenItIsDenied() {
        owner.update(INSERT_PAYMENT, UUID.randomUUID(), UUID.randomUUID(), "PENDING", null, null, null);

        assertThatThrownBy(() -> rls.as(SYSTEM, jdbc -> jdbc.update("delete from payment")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }

    private void rejects(String status, String checkoutId, String checkoutUrl, String externalId, String constraint) {
        assertThatThrownBy(() -> owner.update(INSERT_PAYMENT, UUID.randomUUID(), UUID.randomUUID(), status,
                checkoutId, checkoutUrl, externalId)).as(constraint)
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    private List<UUID> paymentIds(JdbcTemplate jdbc) {
        return jdbc.queryForList("select id from payment", UUID.class);
    }
}
