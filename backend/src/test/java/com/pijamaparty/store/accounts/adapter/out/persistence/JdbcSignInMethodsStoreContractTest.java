package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.LoadSignInMethods;
import com.pijamaparty.store.accounts.application.SignInMethodsStoreContractTest;
import com.pijamaparty.store.accounts.application.UnlinkExternalIdentities;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction. Here every call is its own committed transaction as the customer the
 * methods belong to (RNF-27), so the policies of V5 and V34 apply as in production; the system links the accounts.
 */
@ApiTest
class JdbcSignInMethodsStoreContractTest
    extends SignInMethodsStoreContractTest<JdbcSignInMethodsStoreContractTest.AsCustomer> {

    private final AsCustomer asCustomer;
    private final JpaUserStore users;
    private final JdbcExternalIdentityStore identities;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcSignInMethodsStoreContractTest(JdbcSignInMethodsStore adapter, JpaUserStore users,
                                       JdbcExternalIdentityStore identities,
                                       PlatformTransactionManager transactionManager,
                                       PostgreSQLContainer<?> postgres) {
        this.users = users;
        this.identities = identities;
        this.transactions = new TransactionTemplate(transactionManager);
        this.asCustomer = new AsCustomer(adapter, transactions);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsCustomer createSubject() {
        return asCustomer;
    }

    @Override
    protected void arrange(User user) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> users.save(user)));
    }

    @Override
    protected void arrange(ExternalIdentity identity) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status -> identities.link(identity)));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenAnotherCustomersAccount_whenLoadingItsMethods_thenRowLevelSecurityHidesIt() {
        arrange(identity(ana, GOOGLE));

        Optional<SignInMethods> asBeto = asCustomer.as(beto.id(), () -> asCustomer.adapter().lockByCustomer(ana.id()));

        assertThat(asBeto).isEmpty();
    }

    @Test
    void givenAnotherCustomersLink_whenUnlinkingIt_thenNothingIsUnlinked() {
        ExternalIdentity google = identity(ana, GOOGLE);
        arrange(google);

        asCustomer.as(beto.id(), () -> {
            asCustomer.adapter().unlink(google);
            return null;
        });

        assertThat(owner.queryForObject("select count(*) from external_identity", Long.class)).isOne();
    }

    record AsCustomer(JdbcSignInMethodsStore adapter, TransactionTemplate transactions)
        implements LoadSignInMethods, UnlinkExternalIdentities {

        @Override
        public Optional<SignInMethods> findByCustomer(UserId customer) {
            return as(customer, () -> adapter.findByCustomer(customer));
        }

        @Override
        public Optional<SignInMethods> lockByCustomer(UserId customer) {
            return as(customer, () -> adapter.lockByCustomer(customer));
        }

        @Override
        public void unlink(ExternalIdentity identity) {
            as(identity.userId(), () -> {
                adapter.unlink(identity);
                return null;
            });
        }

        <R> R as(UserId customer, Supplier<R> work) {
            Actor actor = Actor.customer(customer, Optional.empty(), Optional.empty());
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }
    }
}
