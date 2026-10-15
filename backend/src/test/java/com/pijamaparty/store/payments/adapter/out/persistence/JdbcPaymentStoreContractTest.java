package com.pijamaparty.store.payments.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.LoadPayments;
import com.pijamaparty.store.payments.application.PaymentConfirmations;
import com.pijamaparty.store.payments.application.PaymentStoreContractTest;
import com.pijamaparty.store.payments.application.PaymentsToConfirm;
import com.pijamaparty.store.payments.application.SavePayments;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call commits in its own transaction as {@code SYSTEM}, as the payment use cases run, so Row Level Security
 * applies as in production (RNF-27). Read back as the owner.
 */
@ApiTest
class JdbcPaymentStoreContractTest extends PaymentStoreContractTest<JdbcPaymentStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcPaymentStore adapter;
    private final JdbcTemplate owner;

    JdbcPaymentStoreContractTest(JdbcPaymentStore adapter, PlatformTransactionManager transactionManager,
                                 PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.asSystem = new AsSystem(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate payment_checkout, payment");
    }

    @Test
    void givenANewPayment_whenInserted_thenItIsPendingWithTheOrderNumberAndAmountAndNoCheckout() {
        SavePayments.NewPayment payment = pendingPayment(order);

        subject.insertPending(payment);

        Map<String, Object> row = owner.queryForMap("select * from payment where order_id = ?", order);
        assertThat(row).containsEntry("id", payment.id()).containsEntry("provider", "MERCADOPAGO")
            .containsEntry("order_number", payment.orderNumber()).containsEntry("amount_cents", 2_100_050L)
            .containsEntry("status", "PENDING").containsEntry("checkout_id", null).containsEntry("checkout_url", null);
    }

    @Test
    void givenAPendingPayment_whenItsApprovalIsRecorded_thenTheProvidersPaymentIdIsKept() {
        subject.insertPending(pendingPayment(order));

        subject.record(order, MERCADOPAGO, PaymentStatus.APPROVED, "1234567890", NOW.plusSeconds(60));

        Map<String, Object> row = owner.queryForMap("select * from payment where order_id = ?", order);
        assertThat(row).containsEntry("status", "APPROVED").containsEntry("external_payment_id", "1234567890");
    }

    @Test
    void givenAModoPayment_whenAnAttemptGetsItsCheckout_thenTheAttemptRowHoldsItWithItsExpiry() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        var request = new Checkout("modo-abc", URI.create("https://site.modo.com.ar/checkout?id=modo-abc"),
            Optional.of(NOW.plusSeconds(600)));

        subject.recordCheckout(order, MODO, attempt.number(), request, NOW.plusSeconds(1));

        Map<String, Object> row = owner.queryForMap("""
            select c.provider, c.attempt, c.external_reference, c.checkout_id, c.checkout_url, c.expires_at
            from payment_checkout c join payment p on p.id = c.payment_id where p.order_id = ?
            """, order);
        assertThat(row).containsEntry("provider", "MODO").containsEntry("attempt", 1)
            .containsEntry("external_reference", attempt.externalReference())
            .containsEntry("checkout_id", "modo-abc")
            .containsEntry("checkout_url", "https://site.modo.com.ar/checkout?id=modo-abc");
        assertThat(((Timestamp) row.get("expires_at")).toInstant()).isEqualTo(NOW.plusSeconds(600));
        assertThat(owner.queryForObject("select checkout_id from payment where order_id = ?", String.class, order))
            .isEqualTo("modo-abc");
    }

    @Test
    void givenAPayment_whenTwoAttemptsStartAtOnce_thenEachGetsItsOwnNumber() throws Exception {
        subject.insertPending(pendingPayment(order, MODO));
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Optional<CheckoutAttempt>> attempt = () -> {
                start.await(10, TimeUnit.SECONDS);
                return subject.startAttempt(order, MODO, NOW);
            };
            Future<Optional<CheckoutAttempt>> first = executor.submit(attempt);
            Future<Optional<CheckoutAttempt>> second = executor.submit(attempt);
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS).orElseThrow().number(),
                second.get(10, TimeUnit.SECONDS).orElseThrow().number())).containsExactlyInAnyOrder(1, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void givenAGuest_whenInsertingAPayment_thenRowLevelSecurityRefusesIt() {
        var guest = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> CurrentActor.runAs(guest, () -> adapter.insertPending(pendingPayment(order))))
            .isInstanceOf(RuntimeException.class);
        assertThat(owner.queryForObject("select count(*) from payment", Long.class)).isZero();
    }

    @Test
    void givenNoTransaction_whenAPaymentIsLocked_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(() -> adapter.lock(MERCADOPAGO, 1001)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void givenAPaymentLockedByAnotherNotification_whenLockedAgain_thenItWaitsForTheFirstToFinish()
        throws Exception {
        SavePayments.NewPayment payment = pendingPayment(order);
        subject.insertPending(payment);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> asSystem.inTransaction(() -> {
                adapter.lock(MERCADOPAGO, payment.orderNumber());
                locked.countDown();
                await(release);
                return adapter.record(order, MERCADOPAGO, PaymentStatus.APPROVED, "222", NOW);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Optional<PaymentsToConfirm.PaymentToConfirm>> second = executor.submit(() ->
                asSystem.lock(MERCADOPAGO, payment.orderNumber()));

            Thread.sleep(300);
            assertThat(second.isDone()).isFalse();
            release.countDown();

            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(PaymentStatus.APPROVED));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The system, as the payment use cases run (CurrentActor.callAsSystem); each call its own transaction. */
    record AsSystem(JdbcPaymentStore adapter, TransactionTemplate transactions)
        implements LoadPayments, SavePayments, PaymentsToConfirm, PaymentConfirmations {

        <R> R inTransaction(Supplier<R> work) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> work.get()));
        }

        @Override
        public Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider) {
            return CurrentActor.callAsSystem(() -> adapter.checkoutOf(orderId, provider));
        }

        @Override
        public void insertPending(NewPayment payment) {
            CurrentActor.runAsSystem(() -> adapter.insertPending(payment));
        }

        @Override
        public Optional<CheckoutAttempt> startAttempt(UUID orderId, PaymentProvider provider, Instant at) {
            return CurrentActor.callAsSystem(() -> adapter.startAttempt(orderId, provider, at));
        }

        @Override
        public boolean recordCheckout(UUID orderId, PaymentProvider provider, int attempt, Checkout checkout,
                                      Instant at) {
            return CurrentActor.callAsSystem(() -> adapter.recordCheckout(orderId, provider, attempt, checkout, at));
        }

        @Override
        public Optional<PaymentToConfirm> lock(PaymentProvider provider, long orderNumber) {
            return inTransaction(() -> adapter.lock(provider, orderNumber));
        }

        @Override
        public boolean record(UUID orderId, PaymentProvider provider, PaymentStatus status, String externalPaymentId,
                              Instant at) {
            return Boolean.TRUE.equals(inTransaction(() ->
                adapter.record(orderId, provider, status, externalPaymentId, at)));
        }
    }
}
