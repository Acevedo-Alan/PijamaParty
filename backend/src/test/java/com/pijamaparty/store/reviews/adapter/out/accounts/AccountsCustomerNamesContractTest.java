package com.pijamaparty.store.reviews.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.application.CustomerNames;
import com.pijamaparty.store.reviews.application.CustomerNamesContractTest;
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
 * Through accounts.api on PostgreSQL, as the customer itself in a committed transaction, so Row Level Security shows
 * it only its own account (RNF-27).
 */
@ApiTest
class AccountsCustomerNamesContractTest extends CustomerNamesContractTest<AccountsCustomerNamesContractTest.AsCustomer> {

    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, '{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?, 'CUSTOMER', 0,
                now(), now())
        """;

    private final AsCustomer asCustomer;
    private final JdbcTemplate owner;

    AccountsCustomerNamesContractTest(AccountsCustomerNames adapter, PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.asCustomer = new AsCustomer(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsCustomer createSubject() {
        return asCustomer;
    }

    @Override
    protected UserId givenCustomer(String name) {
        UserId customer = UserId.of(UUID.randomUUID());
        owner.update(INSERT_USER, customer.value(), customer.value() + "@example.com", name);
        return customer;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenAnotherCustomer_whenACustomerAsksItsName_thenRowLevelSecurityHidesIt() {
        UserId ana = givenCustomer("Ana");
        UserId bruno = givenCustomer("Bruno");

        Optional<String> seen = CurrentActor.callAs(Actor.customer(ana, Optional.empty(), Optional.empty()),
            () -> asCustomer.transactions().execute(status -> asCustomer.adapter().nameOf(bruno)));

        assertThat(seen).isEmpty();
    }

    record AsCustomer(AccountsCustomerNames adapter, TransactionTemplate transactions) implements CustomerNames {

        @Override
        public Optional<String> nameOf(UserId customer) {
            return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.nameOf(customer)));
        }
    }
}
