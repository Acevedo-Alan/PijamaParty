package com.pijamaparty.store.ordering.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.RememberCheckoutDetails;
import com.pijamaparty.store.ordering.application.RememberCheckoutDetailsContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the accounts module to its tables in PostgreSQL. Each call is its own committed
 * transaction as the customer, as the checkout runs (RNF-27), so Row Level Security and the update guard of
 * {@code app_user} (V29) apply as in production.
 */
@ApiTest
class AccountsModuleCheckoutDetailsContractTest
    extends RememberCheckoutDetailsContractTest<RememberCheckoutDetails> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AccountsModuleCheckoutDetails adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    AccountsModuleCheckoutDetailsContractTest(AccountsModuleCheckoutDetails adapter,
                                              PlatformTransactionManager transactionManager,
                                              PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected RememberCheckoutDetails createSubject() {
        return (customer, phone, address) -> CurrentActor.runAs(asCustomer(customer),
            () -> transactions.executeWithoutResult(status -> adapter.remember(customer, phone, address)));
    }

    @Override
    protected UserId givenCustomer() {
        UserId customer = UserId.of(UUID.randomUUID());
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, customer.value(), customer.value() + "@example.com", PASSWORD_HASH);
        return customer;
    }

    @Override
    protected void givenFullAddressBook(UserId customer) {
        for (int i = 0; i < 10; i++) {
            owner.update("""
                insert into address (id, user_id, street, street_number, city, province, postal_code, is_primary,
                                     created_at, updated_at)
                values (?, ?, ?, '100', 'Santa Fe', 'SANTA_FE', '3000', ?, now(), now())
                """, UUID.randomUUID(), customer.value(), "Calle " + i, i == 0);
        }
    }

    @Override
    protected Optional<String> phoneOf(UserId customer) {
        return owner.queryForList("select phone from app_user where id = ?", String.class, customer.value()).stream()
            .filter(phone -> phone != null).findFirst();
    }

    @Override
    protected List<String> streetsOf(UserId customer) {
        return owner.queryForList("select street from address where user_id = ? order by created_at, street",
            String.class, customer.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, admin_recovery_code, app_user");
    }

    @Test
    void givenAnotherCustomer_whenItsSessionRemembersACheckoutForThisOne_thenThisAccountIsUntouched() {
        UserId ana = givenCustomer();
        UserId bruno = givenCustomer();

        CurrentActor.runAs(asCustomer(bruno), () -> transactions.executeWithoutResult(status ->
            adapter.remember(ana, "3425551234", Optional.of(ADDRESS))));

        assertThat(phoneOf(ana)).isEmpty();
        assertThat(streetsOf(ana)).isEmpty();
    }

    @Test
    void givenNoTransaction_whenACheckoutIsRemembered_thenItIsRefused() {
        UserId customer = givenCustomer();

        assertThatThrownBy(() -> adapter.remember(customer, "3425551234", Optional.empty()))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static Actor asCustomer(UserId customer) {
        return Actor.customer(customer, Optional.empty(), Optional.empty());
    }
}
