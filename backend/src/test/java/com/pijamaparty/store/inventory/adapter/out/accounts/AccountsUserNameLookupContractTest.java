package com.pijamaparty.store.inventory.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.application.UserNameLookup;
import com.pijamaparty.store.inventory.application.UserNameLookupContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through accounts.api on PostgreSQL, as the administrator in a committed transaction, so Row Level Security decides
 * which users are visible (RNF-27).
 */
@ApiTest
class AccountsUserNameLookupContractTest
    extends UserNameLookupContractTest<AccountsUserNameLookupContractTest.AsAdministrator> {

    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, '{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?, ?, 0, now(), now())
        """;

    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    private final AsAdministrator asAdministrator;
    private final JdbcTemplate owner;

    AccountsUserNameLookupContractTest(AccountsUserNameLookup adapter, PlatformTransactionManager transactionManager,
                                       PostgreSQLContainer<?> postgres) {
        this.asAdministrator = new AsAdministrator(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdministrator createSubject() {
        return asAdministrator;
    }

    /** The administrator sees only its own user. */
    @Override
    protected UserId givenVisibleUser(String name) {
        owner.update(INSERT_USER, ADMIN.value(), "owner@example.com", name, "ADMIN");
        return ADMIN;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, admin_recovery_code, app_user");
    }

    @Test
    void givenACustomer_whenTheAdministratorLooksUpItsName_thenRowLevelSecurityHidesIt() {
        UserId admin = givenVisibleUser("Dueña");
        UserId customer = UserId.of(UUID.randomUUID());
        owner.update(INSERT_USER, customer.value(), "ana@example.com", "Ana", "CUSTOMER");

        assertThat(subject.namesOf(Set.of(admin, customer))).isEqualTo(Map.of(admin, "Dueña"));
    }

    record AsAdministrator(AccountsUserNameLookup adapter, TransactionTemplate transactions) implements UserNameLookup {

        @Override
        public Map<UserId, String> namesOf(Set<UserId> ids) {
            return CurrentActor.callAs(Actor.admin(ADMIN),
                () -> transactions.execute(status -> adapter.namesOf(ids)));
        }
    }
}
