package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.UserSignInStoreContractTest;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@ApiTest
class JpaUserSignInStoreContractTest extends UserSignInStoreContractTest<JpaUserSignInStore> {

    private final JpaUserSignInStore adapter;
    private final JpaUserStore users;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JpaUserSignInStoreContractTest(JpaUserSignInStore adapter, JpaUserStore users,
                                   PlatformTransactionManager transactionManager, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected JpaUserSignInStore createSubject() {
        return adapter;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @Override
    protected Optional<User> stored(UserId id) {
        return CurrentActor.callAsSystem(() -> transactions.execute(status -> users.findById(id)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, admin_recovery_code, app_user");
    }

    @Test
    void givenTwoAttemptsOnTheSameAccountAtOnce_whenBothFail_thenBothAreCounted() throws Exception {
        User user = UserStoreContractTest.administrator();
        arrange(user);
        Email email = user.email();
        Instant later = UserStoreContractTest.NOW.plusSeconds(60);
        var start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            var attempts = List.of(
                CompletableFuture.runAsync(() -> failAfter(start, email, later), threads),
                CompletableFuture.runAsync(() -> failAfter(start, email, later), threads));
            start.countDown();
            for (var attempt : attempts) {
                attempt.get(10, TimeUnit.SECONDS);
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(stored(user.id()).orElseThrow().failedSignInAttempts()).isEqualTo(2);
    }

    private void failAfter(CountDownLatch start, Email email, Instant now) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        subject.withUserLockedForSignIn(email, found -> found.orElseThrow().registerFailedSignIn(now));
    }
}
