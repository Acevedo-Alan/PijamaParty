package com.pijamaparty.store.payments.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.LoadPayments;
import com.pijamaparty.store.payments.application.PaymentStoreContractTest;
import com.pijamaparty.store.payments.application.SavePayments;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call commits in its own transaction as {@code SYSTEM}, as the payment use case runs outside any transaction,
 * so Row Level Security applies as in production (RNF-27). Read back as the owner.
 */
@ApiTest
class JdbcPaymentStoreContractTest extends PaymentStoreContractTest<JdbcPaymentStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcPaymentStore adapter;
    private final JdbcTemplate owner;

    JdbcPaymentStoreContractTest(JdbcPaymentStore adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.asSystem = new AsSystem(adapter);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate payment");
    }

    @Test
    void givenANewPayment_whenInserted_thenItIsPendingWithTheOrderAmountAndNoCheckout() {
        SavePayments.NewPayment payment = pendingPayment(order);

        subject.insertPending(payment);

        Map<String, Object> row = owner.queryForMap("select * from payment where order_id = ?", order);
        assertThat(row).containsEntry("id", payment.id()).containsEntry("provider", "MERCADOPAGO")
            .containsEntry("amount_cents", 2_100_050L).containsEntry("status", "PENDING")
            .containsEntry("checkout_id", null).containsEntry("checkout_url", null);
    }

    @Test
    void givenAGuest_whenInsertingAPayment_thenRowLevelSecurityRefusesIt() {
        var guest = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> CurrentActor.runAs(guest, () -> adapter.insertPending(pendingPayment(order))))
            .isInstanceOf(RuntimeException.class);
        assertThat(owner.queryForObject("select count(*) from payment", Long.class)).isZero();
    }

    /** The system, as the payment use case runs (CurrentActor.callAsSystem); each call its own transaction. */
    record AsSystem(JdbcPaymentStore adapter) implements LoadPayments, SavePayments {

        @Override
        public Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider) {
            return CurrentActor.callAsSystem(() -> adapter.checkoutOf(orderId, provider));
        }

        @Override
        public void insertPending(NewPayment payment) {
            CurrentActor.runAsSystem(() -> adapter.insertPending(payment));
        }

        @Override
        public boolean recordCheckout(UUID orderId, PaymentProvider provider, Checkout checkout, Instant at) {
            return CurrentActor.callAsSystem(() -> adapter.recordCheckout(orderId, provider, checkout, at));
        }
    }
}
