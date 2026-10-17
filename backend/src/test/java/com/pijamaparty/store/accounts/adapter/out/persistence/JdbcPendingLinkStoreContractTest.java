package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.LoadPendingLinks;
import com.pijamaparty.store.accounts.application.PendingLinkStoreContractTest;
import com.pijamaparty.store.accounts.application.SavePendingLinks;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction. Here every call is its own committed transaction as SYSTEM, the
 * actor that keeps, reads and forgets pending links (RNF-27), so the policies of V35 apply as in production.
 */
@ApiTest
class JdbcPendingLinkStoreContractTest extends PendingLinkStoreContractTest<JdbcPendingLinkStoreContractTest.AsSystem> {

    private final JdbcPendingLinkStore adapter;
    private final AsSystem asSystem;
    private final JpaUserStore users;
    private final TransactionTemplate transactions;
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate owner;

    JdbcPendingLinkStoreContractTest(JdbcPendingLinkStore adapter, JpaUserStore users,
                                     PlatformTransactionManager transactionManager, NamedParameterJdbcTemplate jdbc,
                                     PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.users = users;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.asSystem = new AsSystem(adapter, transactions);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenTheAdministrator_whenALinkIsRequestedForIt_thenTheDatabaseRefusesIt() {
        User administrator = UserStoreContractTest.administrator();
        arrange(administrator);
        var link = new PendingLink(administrator.id(), new ExternalAccount(ExternalProvider.FACEBOOK, "1"),
            UserStoreContractTest.NOW);

        assertThatThrownBy(() -> subject.request(link)).hasStackTraceContaining("row-level security");

        assertThat(subject.find(administrator.id())).isEmpty();
    }

    @Test
    void givenACustomerLockedByOneRequest_whenAnotherLocksIt_thenItWaitsForTheFirstToEnd() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = Executors.newSingleThreadExecutor();
        try {
            Future<?> holding = first.submit(() -> CurrentActor.runAsSystem(() ->
                transactions.executeWithoutResult(status -> {
                    adapter.lockByCustomer(ana.id());
                    locked.countDown();
                    await(release);
                })));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> {
                jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '200ms'");
                adapter.lockByCustomer(ana.id());
            }))).hasStackTraceContaining("lock timeout");

            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
            assertThat(subject.lockByCustomer(ana.id())).isEmpty();
        } finally {
            release.countDown();
            first.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record AsSystem(JdbcPendingLinkStore adapter, TransactionTemplate transactions)
        implements LoadPendingLinks, SavePendingLinks {

        @Override
        public Optional<PendingLink> find(UserId user) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.find(user)));
        }

        @Override
        public Optional<PendingLink> lockByCustomer(UserId user) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.lockByCustomer(user)));
        }

        @Override
        public void request(PendingLink link) {
            CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> adapter.request(link)));
        }

        @Override
        public void forget(UserId user) {
            CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> adapter.forget(user)));
        }
    }
}
