package com.pijamaparty.store.payments.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-01, T-E3-07, T-E3-15: the payments schema (V15, V18, V22) enforces the UC-14 and UC-15 rules. Its Row Level
 * Security policies (RNF-27) are {@link PaymentsRowLevelSecurityTest}.
 */
@ApiTest
class PaymentsSchemaTest {

    private static final String INSERT_PAYMENT = """
        insert into payment (id, order_id, order_number, provider, amount_cents, status, checkout_id, checkout_url,
                             external_payment_id, created_at, updated_at)
        values (?, ?, nextval('order_number_seq'), 'MERCADOPAGO', 2100000, ?, ?, ?, ?, now(), now())
        """;

    private static final String INSERT_EVENT = """
        insert into payment_event (provider, external_payment_id, status, order_number, received_at)
        values ('MERCADOPAGO', ?, ?, 1001, now())
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
        owner.execute("truncate payment_event, payment");
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
    void givenAPaymentOfAnOrderNumber_whenAnotherPaymentOfTheProviderHasTheSameNumber_thenItIsRejected() {
        String insert = """
            insert into payment (id, order_id, order_number, provider, amount_cents, status, created_at, updated_at)
            values (?, ?, ?, 'MERCADOPAGO', 2100000, 'PENDING', now(), now())
            """;
        owner.update(insert, UUID.randomUUID(), UUID.randomUUID(), 1001L);

        assertThatThrownBy(() -> owner.update(insert, UUID.randomUUID(), UUID.randomUUID(), 1001L))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("payment_provider_order_number_uk");
        assertThatThrownBy(() -> owner.update(insert, UUID.randomUUID(), UUID.randomUUID(), 0L))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("payment_order_number_ck");
    }

    @Test
    void givenInconsistentPaymentData_whenStored_thenItIsRejected() {
        rejects("PENDING", "123-abc", null, null, "payment_checkout_ck");
        rejects("PENDING", "123-abc", "http://example.com/checkout", null, "payment_checkout_url_ck");
        rejects("APPROVED", null, null, null, "payment_approved_ck");
        rejects("REFUNDED", null, null, "987654", "payment_status_ck");
    }

    @Test
    void givenANotificationRecorded_whenTheSameProviderPaymentAndStatusIsStoredAgain_thenItIsRejected() {
        owner.update(INSERT_EVENT, "111", "APPROVED");

        assertThatThrownBy(() -> owner.update(INSERT_EVENT, "111", "APPROVED"))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("payment_event_pk");
        assertThatThrownBy(() -> owner.update(INSERT_EVENT, "111", "REFUNDED"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("payment_event_status_ck");
        assertThat(owner.update(INSERT_EVENT, "111", "PENDING")).isOne();
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
