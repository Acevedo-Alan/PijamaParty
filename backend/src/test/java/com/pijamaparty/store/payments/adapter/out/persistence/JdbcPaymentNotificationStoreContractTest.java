package com.pijamaparty.store.payments.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentNotifications;
import com.pijamaparty.store.payments.application.PaymentNotificationsContractTest;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
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
 * Each call commits in its own transaction as {@code SYSTEM}, as the webhook runs, so Row Level Security applies as in
 * production (RNF-27). Read back as the owner.
 */
@ApiTest
class JdbcPaymentNotificationStoreContractTest
    extends PaymentNotificationsContractTest<JdbcPaymentNotificationStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcPaymentNotificationStore adapter;
    private final JdbcTemplate owner;

    JdbcPaymentNotificationStoreContractTest(JdbcPaymentNotificationStore adapter,
                                             PlatformTransactionManager transactionManager,
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
        owner.execute("truncate payment_event");
    }

    @Test
    void givenANewNotification_whenRecorded_thenItsPaymentStatusOrderNumberAndTimeAreStored() {
        subject.recordFirst(MERCADOPAGO, new VerifiedPayment("9876543210", 1001, VerifiedStatus.APPROVED,
            Money.of(2_100_050)), 1001, NOW);

        Map<String, Object> row = owner.queryForMap("select * from payment_event");
        assertThat(row).containsEntry("provider", "MERCADOPAGO").containsEntry("external_payment_id", "9876543210")
            .containsEntry("status", "APPROVED").containsEntry("order_number", 1001L);
        assertThat(owner.queryForObject("select received_at from payment_event", OffsetDateTime.class).toInstant())
            .isEqualTo(NOW);
    }

    @Test
    void givenATransactionThatRecordedANotificationAndFailed_whenTheNotificationArrivesAgain_thenItIsToBeProcessed() {
        assertThatThrownBy(() -> asSystem.inTransaction(() -> {
            adapter.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), 1001, NOW);
            throw new IllegalStateException("the order could not be paid");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), 1001,

                NOW.plusSeconds(900)))
            .isTrue();
    }

    @Test
    void givenANotificationBeingRecordedByAnotherTransaction_whenRecordedAgain_thenItWaitsAndWasAlreadyProcessed()
        throws Exception {
        var recorded = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> asSystem.inTransaction(() -> {
                boolean inserted =
                    adapter.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), 1001, NOW);
                recorded.countDown();
                await(release);
                return inserted;
            }));
            assertThat(recorded.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() ->
                subject.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), 1001, NOW));

            Thread.sleep(300);
            assertThat(second.isDone()).isFalse();
            release.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(owner.queryForObject("select count(*) from payment_event", Long.class)).isOne();
    }

    @Test
    void givenNoTransaction_whenANotificationIsRecorded_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAsSystem(() ->
            adapter.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), 1001, NOW)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void givenAGuest_whenRecordingANotification_thenRowLevelSecurityRefusesIt() {
        var guest = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> CurrentActor.callAs(guest, () -> asSystem.transactions().execute(status ->
            adapter.recordFirst(MERCADOPAGO, verified("111", VerifiedStatus.APPROVED), 1001, NOW))))
            .isInstanceOf(RuntimeException.class);
        assertThat(owner.queryForObject("select count(*) from payment_event", Long.class)).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The system, as the webhook runs (CurrentActor.callAsSystem); each call its own transaction. */
    record AsSystem(JdbcPaymentNotificationStore adapter, TransactionTemplate transactions)
        implements PaymentNotifications {

        <R> R inTransaction(Supplier<R> work) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> work.get()));
        }

        @Override
        public boolean recordFirst(PaymentProvider provider, VerifiedPayment verified, long orderNumber,
                                   Instant receivedAt) {
            return Boolean.TRUE.equals(inTransaction(() ->
                adapter.recordFirst(provider, verified, orderNumber, receivedAt)));
        }
    }
}
