package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.LoadUser;
import com.pijamaparty.store.accounts.application.SaveUser;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
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
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenACustomerThatRememberedItsPhone_whenTheSystemSavesItsSignIn_thenThePhoneIsKept() {
        UserId id = UserId.of(UUID.randomUUID());
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'ana@example.com', true, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, id.value(), HASH.value());
        Actor ana = Actor.customer(id, Optional.empty(), Optional.empty());
        CurrentActor.runAs(ana, () -> asSystem.transactions().executeWithoutResult(status -> {
            User user = asSystem.adapter().findById(id).orElseThrow();
            user.rememberPhone("3425551234", NOW);
            asSystem.adapter().save(user);
        }));

        User loaded = asSystem.findById(id).orElseThrow();
        loaded.registerSuccessfulSignIn(NOW);
        asSystem.save(loaded);

        assertThat(asSystem.findById(id).orElseThrow().phone()).contains("3425551234");
        assertThat(owner.queryForObject("select phone from app_user", String.class)).isEqualTo("3425551234");
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
