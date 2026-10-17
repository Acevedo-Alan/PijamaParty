package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.AddressBookStoreContractTest;
import com.pijamaparty.store.accounts.application.LoadAddressBook;
import com.pijamaparty.store.accounts.application.SaveAddressBook;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressDetails;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the customer who owns the book, so Row Level Security applies as in
 * production (RNF-27), then read back from PostgreSQL.
 */
@ApiTest
class JdbcAddressBookStoreContractTest extends AddressBookStoreContractTest<JdbcAddressBookStoreContractTest.AsOwner> {

    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");
    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AsOwner asOwner;
    private final JdbcTemplate owner;

    JdbcAddressBookStoreContractTest(JdbcAddressBookStore adapter, PlatformTransactionManager transactionManager,
                                     PostgreSQLContainer<?> postgres) {
        this.asOwner = new AsOwner(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsOwner createSubject() {
        return asOwner;
    }

    @Override
    protected void arrangeAccount(UserId customer) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, customer.value(), customer.value() + "@example.com", PASSWORD_HASH);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, admin_recovery_code, app_user");
    }

    private static AddressBook bookWithOneAddress(UserId customer) {
        AddressBook book = AddressBook.of(customer, List.of());
        book.add(AddressId.of(UUID.randomUUID()), new AddressDetails("San Martín", "1234", Optional.empty(),
            Optional.empty(), "Santa Fe", Province.SANTA_FE, "S3000ABC"), false, T0);
        return book;
    }

    @Test
    void givenAnotherCustomersBook_whenACustomerReadsOrLocksIt_thenItSeesNoAddressAndNoAccount() {
        asOwner.save(bookWithOneAddress(ANA));

        assertThat(asOwner.as(asCustomer(BRUNO), () -> asOwner.adapter().findByCustomer(ANA).addresses())).isEmpty();
        assertThat(asOwner.as(asCustomer(BRUNO), () -> asOwner.adapter().lockByCustomer(ANA))).isEmpty();
    }

    @Test
    void givenABookOfAnotherCustomer_whenACustomerSavesIt_thenRowLevelSecurityRejectsIt() {
        AddressBook anas = bookWithOneAddress(ANA);

        assertThatThrownBy(() -> asOwner.as(asCustomer(BRUNO), () -> {
            asOwner.adapter().save(anas);
            return null;
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThat(owner.queryForObject("select count(*) from address", Long.class)).isZero();
    }

    @Test
    void givenAnAddressInTheBook_thenNoOtherTableReferencesIt() {
        // UC-20: an order keeps its own copy of the address (R-04), so deleting an address never touches an order.
        assertThat(owner.queryForObject("""
            select count(*)
            from pg_constraint
            where contype = 'f' and confrelid = 'address'::regclass
            """, Long.class)).isZero();
    }

    private static Actor asCustomer(UserId customer) {
        return Actor.customer(customer, Optional.empty(), Optional.empty());
    }

    record AsOwner(JdbcAddressBookStore adapter, TransactionTemplate transactions)
        implements LoadAddressBook, SaveAddressBook {

        @Override
        public AddressBook findByCustomer(UserId customer) {
            return as(asCustomer(customer), () -> adapter.findByCustomer(customer));
        }

        @Override
        public Optional<AddressBook> lockByCustomer(UserId customer) {
            return as(asCustomer(customer), () -> adapter.lockByCustomer(customer));
        }

        @Override
        public void save(AddressBook book) {
            as(asCustomer(book.owner()), () -> {
                adapter.save(book);
                return null;
            });
        }

        <R> R as(Actor actor, Supplier<R> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }
    }
}
