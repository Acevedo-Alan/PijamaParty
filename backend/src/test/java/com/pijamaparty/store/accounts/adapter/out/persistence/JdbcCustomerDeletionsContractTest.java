package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.DeleteCustomers;
import com.pijamaparty.store.accounts.application.DeleteCustomersContractTest;
import com.pijamaparty.store.accounts.application.LoadSignInMethods;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * account belongs to (RNF-27), so the policy of V36 and the foreign keys apply as in production; the system creates
 * the customers and links their accounts.
 */
@ApiTest
class JdbcCustomerDeletionsContractTest
    extends DeleteCustomersContractTest<JdbcCustomerDeletionsContractTest.AsCustomer> {

    private final AsCustomer asCustomer;
    private final JpaUserStore users;
    private final JdbcExternalIdentityStore identities;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcCustomerDeletionsContractTest(JdbcCustomerDeletions adapter, JdbcSignInMethodsStore methods,
                                      JpaUserStore users, JdbcExternalIdentityStore identities,
                                      PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.users = users;
        this.identities = identities;
        this.transactions = new TransactionTemplate(transactionManager);
        this.asCustomer = new AsCustomer(adapter, methods, transactions);
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
    void givenACustomerWithAnAddressATokenAndAWaitingLink_whenDeleted_thenTheyGoWithIt() {
        owner.update("""
            insert into address (id, user_id, street, street_number, city, province, postal_code, is_primary,
                                 created_at, updated_at)
            values (?, ?, 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', 'S3000ABC', true, now(), now())
            """, UUID.randomUUID(), ana.id().value());
        owner.update("""
            insert into user_token (id, user_id, purpose, token_hash, created_at, expires_at)
            values (?, ?, 'PASSWORD_RESET', ?, now(), now() + interval '30 minutes')
            """, UUID.randomUUID(), ana.id().value(), "a".repeat(64));
        owner.update("""
            insert into pending_link (user_id, provider, subject, requested_at) values (?, 'FACEBOOK', ?, now())
            """, ana.id().value(), "10229876543210");

        assertThat(asCustomer.delete(ana.id())).isTrue();

        for (String rowsOfAna : List.of("select count(*) from address where user_id = ?",
            "select count(*) from user_token where user_id = ?", "select count(*) from pending_link where user_id = ?",
            "select count(*) from external_identity where user_id = ?")) {
            assertThat(owner.queryForObject(rowsOfAna, Integer.class, ana.id().value())).as(rowsOfAna).isZero();
        }
    }

    @Test
    void givenAnotherCustomer_whenDeletingItsAccount_thenRowLevelSecurityKeepsIt() {
        boolean deleted = asCustomer.as(beto.id(), () -> asCustomer.adapter().delete(ana.id()));

        assertThat(deleted).isFalse();
        assertThat(owner.queryForObject("select count(*) from app_user where id = ?", Integer.class, ana.id().value()))
            .isOne();
    }

    record AsCustomer(JdbcCustomerDeletions adapter, JdbcSignInMethodsStore methods, TransactionTemplate transactions)
        implements DeleteCustomers, LoadSignInMethods {

        @Override
        public boolean delete(UserId customer) {
            return as(customer, () -> adapter.delete(customer));
        }

        @Override
        public Optional<SignInMethods> findByCustomer(UserId customer) {
            return as(customer, () -> methods.findByCustomer(customer));
        }

        @Override
        public Optional<SignInMethods> lockByCustomer(UserId customer) {
            return as(customer, () -> methods.lockByCustomer(customer));
        }

        <R> R as(UserId customer, Supplier<R> work) {
            Actor actor = Actor.customer(customer, Optional.empty(), Optional.empty());
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }
    }
}
