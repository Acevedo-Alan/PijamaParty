package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.LoadUser;
import com.pijamaparty.store.accounts.application.SaveUser;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction and security context. Here every call is its own committed
 * transaction as SYSTEM, the actor that creates and reads users at startup and sign-in (RNF-27), so the RLS
 * policies of V5 apply as in production.
 */
@ApiTest
class JpaUserStoreContractTest extends UserStoreContractTest<JpaUserStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcTemplate owner;

    JpaUserStoreContractTest(JpaUserStore adapter, PlatformTransactionManager transactionManager,
                             PostgreSQLContainer<?> postgres) {
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
        owner.execute("truncate address, user_token, admin_recovery_code, app_user");
    }

    record AsSystem(JpaUserStore adapter, TransactionTemplate transactions) implements LoadUser, SaveUser {

        @Override
        public Optional<User> findById(UserId id) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.findById(id)));
        }

        @Override
        public boolean administratorExists() {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.administratorExists()));
        }

        @Override
        public Optional<User> findAdministrator() {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.findAdministrator()));
        }

        @Override
        public void save(User user) {
            CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> adapter.save(user)));
        }
    }
}
