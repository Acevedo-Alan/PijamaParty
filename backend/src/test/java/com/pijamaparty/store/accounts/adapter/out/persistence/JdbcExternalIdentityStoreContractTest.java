package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.ExternalIdentityStoreContractTest;
import com.pijamaparty.store.accounts.application.FindExternalIdentities;
import com.pijamaparty.store.accounts.application.LinkExternalIdentities;
import com.pijamaparty.store.accounts.application.UserStoreContractTest;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction. Here every call is its own committed transaction as SYSTEM, the
 * actor of the return from a provider (RNF-27), so the policies of V34 apply as in production.
 */
@ApiTest
class JdbcExternalIdentityStoreContractTest
    extends ExternalIdentityStoreContractTest<JdbcExternalIdentityStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JpaUserStore users;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcExternalIdentityStoreContractTest(JdbcExternalIdentityStore adapter, JpaUserStore users,
                                          PlatformTransactionManager transactionManager,
                                          PostgreSQLContainer<?> postgres) {
        this.users = users;
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
    void givenTheAdministrator_whenLinkingAnExternalAccountToIt_thenTheDatabaseRefusesIt() {
        User administrator = UserStoreContractTest.administrator();
        arrange(administrator);

        assertThatThrownBy(() -> subject.link(identity(administrator, GOOGLE)))
            .hasStackTraceContaining("row-level security");

        assertThat(subject.find(GOOGLE)).isEmpty();
    }

    @Test
    void givenTwoCustomersRacingForOneExternalAccount_whenBothLinkIt_thenOnlyOneLinksAndTheOtherTransactionGoesOn() {
        assertThat(subject.link(identity(ana, GOOGLE))).isTrue();

        boolean betoLinked = CurrentActor.callAsSystem(() -> transactions.execute(status -> {
            boolean linked = asSystem.adapter().link(identity(beto, GOOGLE));
            // The transaction is not aborted: it can still link another account.
            asSystem.adapter().link(identity(beto, FACEBOOK));
            return linked;
        }));

        assertThat(betoLinked).isFalse();
        assertThat(subject.find(FACEBOOK).orElseThrow().userId()).isEqualTo(beto.id());
    }

    record AsSystem(JdbcExternalIdentityStore adapter, TransactionTemplate transactions)
        implements FindExternalIdentities, LinkExternalIdentities {

        @Override
        public Optional<ExternalIdentity> find(ExternalAccount account) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.find(account)));
        }

        @Override
        public boolean hasProvider(UserId user, ExternalProvider provider) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.hasProvider(user, provider)));
        }

        @Override
        public boolean link(ExternalIdentity identity) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.link(identity)));
        }
    }
}
