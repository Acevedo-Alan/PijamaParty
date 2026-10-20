package com.pijamaparty.store.accounts.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E4-11: the Row Level Security policies of {@code app_user} (V5, V29), {@code address} and {@code user_token}
 * (V29), tested for reads, inserts, updates and deletes with customer A, customer B, a guest, the administrator, the
 * system and a missing context (RNF-27). A customer reads and updates only its own user and does everything on its
 * own addresses; the administrator reads its own user and every address; the system reads and updates every user,
 * registers customers and is the only one to reach the tokens. T-E4-16 lets a customer delete its own user (V36),
 * whose addresses, tokens, external accounts and waiting link go with it; nobody else deletes a user. T-E4-13 adds
 * {@code external_identity} (V34): a customer reads and unlinks its own external accounts, and only the system reads
 * all of them and links them. T-E4-14 adds {@code pending_link} (V35), which only the system reaches, and only for
 * customers. Customer B also reaches for customer A's orders and addresses by their ids, as a query that forgot to filter by customer would. Which columns
 * each actor may change is {@link AccountSchemaTest} and {@link UserSchemaTest}. Every transaction is committed by
 * the application role.
 */
@ApiTest
class AccountRowLevelSecurityTest {

    private static final String PASSWORD = "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, ?, ?, 'Test', ?, 0, now(), now())
        """;
    private static final String INSERT_ADDRESS = """
        insert into address (id, user_id, street, street_number, floor, apartment, city, province, postal_code,
                             is_primary, created_at, updated_at)
        values (?, ?, 'San Martín', '1234', null, null, 'Santa Fe', 'SANTA_FE', 'S3000ABC', ?, now(), now())
        """;
    private static final String INSERT_TOKEN = """
        insert into user_token (id, user_id, purpose, token_hash, created_at, expires_at)
        values (?, ?, ?, ?, now(), now() + interval '24 hours')
        """;
    private static final String INSERT_ORDER = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name,
                            customer_last_name, customer_email,
                            customer_phone, national_id, street, street_number, city, province, postal_code,
                            subtotal_cents, discount_cents, shipping_cents, total_cents, payment_provider,
                            terms_version, reservation_expires_at, created_at, updated_at)
        values (?, nextval('order_number_seq'), ?, ?, 'AWAITING_PAYMENT', 'Ana', 'Pérez', 'a@example.com', '3425551234',
                '30123456', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000', 2000000, 0, 300000, 2300000,
                'MERCADOPAGO', '2026-10', now() + interval '30 minutes', now(), now())
        """;
    private static final String INSERT_IDENTITY = """
        insert into external_identity (id, user_id, provider, subject, email, created_at)
        values (?, ?, ?, ?, ?, now())
        """;
    private static final String INSERT_PENDING_LINK = """
        insert into pending_link (user_id, provider, subject, requested_at) values (?, 'FACEBOOK', ?, now())
        """;
    private static final String RLS_VIOLATION = "row-level security";

    /**
     * The seeded accounts: both customers have an address, an unused verification token, a linked Google account and a
     * Facebook account waiting for its link confirmation; the owner has none.
     */
    enum Account {
        CUSTOMER_A(CUSTOMER_A_ID, "a@example.com", "CUSTOMER"),
        CUSTOMER_B(CUSTOMER_B_ID, "b@example.com", "CUSTOMER"),
        ADMIN(ADMIN_ID, "owner@example.com", "ADMIN");

        private final UserId id;
        private final String email;
        private final String role;

        Account(UserId id, String email, String role) {
            this.id = id;
            this.email = email;
            this.role = role;
        }

        boolean isCustomer() {
            return role.equals("CUSTOMER");
        }
    }

    private static final Set<Account> ALL = EnumSet.allOf(Account.class);
    private static final Set<Account> CUSTOMERS = EnumSet.of(Account.CUSTOMER_A, Account.CUSTOMER_B);
    private static final Set<Account> NONE = EnumSet.noneOf(Account.class);

    /**
     * Who runs the transaction ({@code actor} empty: no security context), the users it reads and updates, the
     * customers whose addresses it reads and writes (add, edit and delete), whether it reaches the tokens, the
     * customers whose external accounts it reads and unlinks, and whether it links them.
     */
    record Caller(String name, Optional<Actor> actor, Set<Account> readsUsers, Set<Account> updatesUsers,
                  Set<Account> readsAddresses, Set<Account> writesAddresses, boolean reachesTokens,
                  Set<Account> readsIdentities, Set<Account> unlinksIdentities, boolean linksIdentities) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        boolean isSystem() {
            return actor.filter(a -> a.is(Role.SYSTEM)).isPresent();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A), EnumSet.of(Account.CUSTOMER_A),
                EnumSet.of(Account.CUSTOMER_A), EnumSet.of(Account.CUSTOMER_A), EnumSet.of(Account.CUSTOMER_A), false,
                EnumSet.of(Account.CUSTOMER_A), EnumSet.of(Account.CUSTOMER_A), false),
            new Caller("customer B", Optional.of(CUSTOMER_B), EnumSet.of(Account.CUSTOMER_B),
                EnumSet.of(Account.CUSTOMER_B), EnumSet.of(Account.CUSTOMER_B), EnumSet.of(Account.CUSTOMER_B), false,
                EnumSet.of(Account.CUSTOMER_B), EnumSet.of(Account.CUSTOMER_B), false),
            new Caller("guest", Optional.of(GUEST), NONE, NONE, NONE, NONE, false, NONE, NONE, false),
            new Caller("administrator", Optional.of(ADMIN), EnumSet.of(Account.ADMIN), NONE, CUSTOMERS, NONE, false,
                NONE, NONE, false),
            new Caller("system", Optional.of(SYSTEM), ALL, ALL, NONE, NONE, true, CUSTOMERS, NONE, true),
            new Caller("no context", Optional.empty(), NONE, NONE, NONE, NONE, false, NONE, NONE, false));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final Map<Account, UUID> addresses = new EnumMap<>(Account.class);

    AccountRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Stored by the owner role (no policies). */
    @BeforeEach
    void seedAccounts() {
        for (Account account : Account.values()) {
            owner.update(INSERT_USER, account.id.value(), account.email, true, PASSWORD, account.role);
            if (account.isCustomer()) {
                addresses.put(account, address(account, true));
                owner.update(INSERT_TOKEN, UUID.randomUUID(), account.id.value(), "EMAIL_VERIFICATION",
                    new AccountToken("verification-of-" + account.name()).hash());
                owner.update(INSERT_IDENTITY, UUID.randomUUID(), account.id.value(), "GOOGLE",
                    "google-" + account.name(), account.email);
                owner.update(INSERT_PENDING_LINK, account.id.value(), "facebook-" + account.name());
            }
        }
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenTheAccountTables_whenCheckingTheirProtection_thenRowLevelSecurityIsEnabledAndForced() {
        for (String table : List.of("app_user", "address", "user_token", "external_identity", "pending_link")) {
            var flags = owner.queryForMap(
                "select relrowsecurity, relforcerowsecurity from pg_class where oid = ?::regclass", table);
            assertThat(flags).as(table).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
        }
    }

    // --- app_user ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenEveryAccount_whenReading_thenOnlyTheReachableUsersAddressesAndTokensAreVisible(Caller caller) {
        var users = caller.run(rls, sql -> sql.queryForList("select id from app_user", UUID.class));
        var addressOwners = caller.run(rls, sql -> sql.queryForList("select user_id from address", UUID.class));
        var tokenOwners = caller.run(rls, sql -> sql.queryForList("select user_id from user_token", UUID.class));

        assertThat(accounts(users)).as("users").isEqualTo(caller.readsUsers());
        assertThat(accounts(addressOwners)).as("addresses").isEqualTo(caller.readsAddresses());
        assertThat(accounts(tokenOwners)).as("tokens").isEqualTo(caller.reachesTokens() ? CUSTOMERS : NONE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenEveryAccount_whenUpdatingAllUsers_thenOnlyTheReachableOnesChange(Caller caller) {
        // updated_at is the one column every guard lets through, so the policies alone decide the rows.
        var updated = caller.run(rls, sql -> sql.queryForList(
            "update app_user set updated_at = now() + interval '1 day' returning id", UUID.class));

        assertThat(accounts(updated)).isEqualTo(caller.updatesUsers());
        assertThat(accounts(owner.queryForList("select id from app_user where updated_at > now() + interval '1 hour'",
            UUID.class))).as("stored").isEqualTo(caller.updatesUsers());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoNewAccount_whenRegisteringACustomer_thenOnlyTheSystemIsAccepted(Caller caller) {
        var register = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_USER, UUID.randomUUID(),
            "new@example.com", false, PASSWORD, "CUSTOMER");

        if (caller.isSystem()) {
            assertThat(caller.run(rls, register)).isOne();
        } else {
            assertThatThrownBy(() -> caller.run(rls, register))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenEveryAccount_whenDeletingAllUsers_thenOnlyACustomerDeletesItselfWithItsRows(Caller caller) {
        // A customer updates only itself (updatesUsers), and that is the one user it may delete; nobody else deletes one.
        Set<Account> deletable = caller.actor().filter(actor -> actor.is(Role.CUSTOMER)).isPresent()
            ? caller.updatesUsers() : NONE;

        var deleted = caller.run(rls, sql -> sql.queryForList("delete from app_user returning id", UUID.class));

        assertThat(accounts(deleted)).isEqualTo(deletable);
        Set<Account> remaining = EnumSet.copyOf(ALL);
        remaining.removeAll(deletable);
        assertThat(accounts(owner.queryForList("select id from app_user", UUID.class))).as("stored").isEqualTo(remaining);
        for (String ownersOfRows : List.of("select user_id from address", "select user_id from user_token",
            "select user_id from external_identity", "select user_id from pending_link")) {
            Set<Account> owners = accounts(owner.queryForList(ownersOfRows, UUID.class));
            Set<Account> kept = EnumSet.copyOf(CUSTOMERS);
            kept.removeAll(deletable);
            assertThat(owners).as(ownersOfRows).isEqualTo(kept);
        }
    }

    @Test
    void givenTheSystemConsumingCustomerAsToken_whenVerifyingEmails_thenOnlyCustomerAIsVerified() {
        // The token travels through the real context (Actor.systemConsuming sets app.account_token_hash).
        owner.update("update app_user set email_verified = false where role = 'CUSTOMER'");
        // A linked Google account with the customer's email would verify it without a token (V34).
        owner.update("delete from external_identity");
        var token = new AccountToken("verification-of-" + Account.CUSTOMER_A.name());
        var consuming = Actor.systemConsuming(token);
        String verify = "update app_user set email_verified = true, updated_at = now() where id = ?";

        assertThatThrownBy(() -> rls.as(consuming, sql -> {
                sql.update("update user_token set used_at = now() where token_hash = ?", token.hash());
                return sql.update(verify, CUSTOMER_B_ID.value());
            }))
            .as("customer B with A's token").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
        int verified = rls.as(consuming, sql -> {
            sql.update("update user_token set used_at = now() where token_hash = ?", token.hash());
            return sql.update(verify, CUSTOMER_A_ID.value());
        });

        assertThat(verified).isOne();
        assertThat(owner.queryForList("select id from app_user where email_verified and role = 'CUSTOMER'",
            UUID.class)).containsExactly(CUSTOMER_A_ID.value());
    }

    // --- address ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoNewAddress_whenAddingOneForEachCustomer_thenOnlyItsOwnIsAccepted(Caller caller) {
        for (Account customer : CUSTOMERS) {
            var add = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_ADDRESS, UUID.randomUUID(),
                customer.id.value(), false);
            if (caller.writesAddresses().contains(customer)) {
                assertThat(caller.run(rls, add)).as(customer.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, add)).as(customer.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnAddressOfEachCustomer_whenEditingAll_thenOnlyItsOwnChanges(Caller caller) {
        var edited = caller.run(rls, sql -> sql.queryForList(
            "update address set city = 'Rosario', updated_at = now() returning user_id", UUID.class));

        assertThat(accounts(edited)).isEqualTo(caller.writesAddresses());
        assertThat(accounts(owner.queryForList("select user_id from address where city = 'Rosario'", UUID.class)))
            .as("stored").isEqualTo(caller.writesAddresses());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnAddressOfEachCustomer_whenDeletingAll_thenOnlyItsOwnIsDeleted(Caller caller) {
        var deleted = caller.run(rls, sql -> sql.queryForList("delete from address returning user_id", UUID.class));

        assertThat(accounts(deleted)).isEqualTo(caller.writesAddresses());
        var kept = EnumSet.copyOf(CUSTOMERS);
        kept.removeAll(caller.writesAddresses());
        assertThat(accounts(owner.queryForList("select user_id from address", UUID.class))).as("stored")
            .isEqualTo(kept);
    }

    @Test
    void givenEitherCustomer_whenMovingItsAddressToTheOther_thenItIsRejected() {
        for (var move : List.of(List.of(Account.CUSTOMER_A, Account.CUSTOMER_B),
                List.of(Account.CUSTOMER_B, Account.CUSTOMER_A))) {
            Actor from = move.get(0) == Account.CUSTOMER_A ? CUSTOMER_A : CUSTOMER_B;
            assertThatThrownBy(() -> rls.as(from, sql -> sql.update("update address set user_id = ? where id = ?",
                    move.get(1).id.value(), addresses.get(move.get(0)))))
                .as(move.toString()).isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        }
    }

    // --- user_token ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenATokenOfEachCustomer_whenCreatingUsingAndDeletingTokens_thenOnlyTheSystemReachesThem(Caller caller) {
        var create = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_TOKEN, UUID.randomUUID(),
            CUSTOMER_A_ID.value(), "EMAIL_VERIFICATION", new AccountToken(UUID.randomUUID().toString()).hash());
        if (caller.reachesTokens()) {
            assertThat(caller.run(rls, create)).as("created").isOne();
        } else {
            assertThatThrownBy(() -> caller.run(rls, create)).as("created")
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        }

        var used = caller.run(rls, sql -> sql.queryForList(
            "update user_token set used_at = now() returning user_id", UUID.class));
        var deleted = caller.run(rls, sql -> sql.queryForList("delete from user_token returning user_id", UUID.class));

        // The system also reaches the one it just created, of customer A.
        assertThat(used).as("used").hasSize(caller.reachesTokens() ? 3 : 0);
        assertThat(deleted).as("deleted").hasSize(caller.reachesTokens() ? 3 : 0);
        assertThat(owner.queryForObject("select count(*) from user_token where used_at is null", Integer.class))
            .as("unused and stored").isEqualTo(caller.reachesTokens() ? 0 : CUSTOMERS.size());
    }

    // --- external_identity ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAGoogleAccountOfEachCustomer_whenReading_thenOnlyTheReachableOnesAreVisible(Caller caller) {
        var owners = caller.run(rls, sql -> sql.queryForList("select user_id from external_identity", UUID.class));

        assertThat(accounts(owners)).isEqualTo(caller.readsIdentities());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoFacebookAccount_whenLinkingOneToEachCustomer_thenOnlyTheSystemIsAccepted(Caller caller) {
        for (Account customer : CUSTOMERS) {
            var link = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_IDENTITY, UUID.randomUUID(),
                customer.id.value(), "FACEBOOK", "facebook-" + customer.name(), customer.email);
            if (caller.linksIdentities()) {
                assertThat(caller.run(rls, link)).as(customer.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, link)).as(customer.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAGoogleAccountOfEachCustomer_whenUnlinkingAll_thenOnlyItsOwnIsUnlinked(Caller caller) {
        var unlinked = caller.run(rls, sql -> sql.queryForList(
            "delete from external_identity returning user_id", UUID.class));

        assertThat(accounts(unlinked)).isEqualTo(caller.unlinksIdentities());
        var kept = EnumSet.copyOf(CUSTOMERS);
        kept.removeAll(caller.unlinksIdentities());
        assertThat(accounts(owner.queryForList("select user_id from external_identity", UUID.class)))
            .as("stored").isEqualTo(kept);
    }

    // --- pending_link ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAPendingLinkOfEachCustomer_whenReadingUpdatingAndForgettingAll_thenOnlyTheSystemReachesThem(
        Caller caller) {
        Set<Account> reached = caller.isSystem() ? CUSTOMERS : NONE;

        var read = caller.run(rls, sql -> sql.queryForList("select user_id from pending_link", UUID.class));
        var replaced = caller.run(rls, sql -> sql.queryForList(
            "update pending_link set subject = 'facebook-other', requested_at = now() returning user_id", UUID.class));
        var forgotten = caller.run(rls, sql -> sql.queryForList(
            "delete from pending_link returning user_id", UUID.class));

        assertThat(accounts(read)).as("read").isEqualTo(reached);
        assertThat(accounts(replaced)).as("replaced").isEqualTo(reached);
        assertThat(accounts(forgotten)).as("forgotten").isEqualTo(reached);
        assertThat(owner.queryForObject("select count(*) from pending_link", Integer.class)).as("stored")
            .isEqualTo(caller.isSystem() ? 0 : CUSTOMERS.size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoPendingLink_whenRequestingOneForEveryAccount_thenOnlyTheSystemIsAcceptedAndOnlyForCustomers(
        Caller caller) {
        owner.update("delete from pending_link");
        for (Account account : Account.values()) {
            var request = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_PENDING_LINK, account.id.value(),
                "facebook-" + account.name());
            if (caller.isSystem() && account.isCustomer()) {
                assertThat(caller.run(rls, request)).as(account.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, request)).as(account.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @Test
    void givenAPendingLink_whenTheSystemMovesItToAnotherCustomer_thenTheGuardRefusesIt() {
        owner.update("delete from pending_link where user_id = ?", CUSTOMER_B_ID.value());

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(
                "update pending_link set user_id = ? where user_id = ?", CUSTOMER_B_ID.value(),
                CUSTOMER_A_ID.value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("a pending link belongs to one customer");
    }

    // --- another customer's orders and addresses ---

    @Test
    void givenCustomerAsOrderAndAddress_whenCustomerBReachesForThemById_thenItFindsAndChangesNothing() {
        var order = UUID.randomUUID();
        owner.update(INSERT_ORDER, order, CUSTOMER_A_ID.value(), new TrackingToken("customer-a-order").hash());
        var anasAddress = addresses.get(Account.CUSTOMER_A);

        assertThat(ids(CUSTOMER_B, "select id from orders where id = ?", order)).as("order").isEmpty();
        assertThat(ids(CUSTOMER_B, "select id from address where id = ?", anasAddress)).as("address").isEmpty();
        assertThat(ids(CUSTOMER_B, """
            select a.id
            from app_user u join address a on a.user_id = u.id
            where u.id = ?
            """, CUSTOMER_A_ID.value())).as("A's account and addresses").isEmpty();

        int orderEdited = rls.as(CUSTOMER_B, sql -> sql.update(
            "update orders set customer_email = 'b@example.com' where id = ?", order));
        int addressEdited = rls.as(CUSTOMER_B, sql -> sql.update(
            "update address set city = 'Rosario' where id = ?", anasAddress));
        int addressDeleted = rls.as(CUSTOMER_B, sql -> sql.update("delete from address where id = ?", anasAddress));
        assertThat(List.of(orderEdited, addressEdited, addressDeleted)).containsOnly(0);
        assertThat(owner.queryForObject("select customer_email from orders where id = ?", String.class, order))
            .isEqualTo("a@example.com");
        assertThat(owner.queryForObject("select city from address where id = ?", String.class, anasAddress))
            .isEqualTo("Santa Fe");

        // A's own session still reaches both: the policies, not missing rows, hid them from B.
        assertThat(ids(CUSTOMER_A, "select id from orders where id = ?", order)).containsExactly(order);
        assertThat(ids(CUSTOMER_A, "select id from address where id = ?", anasAddress)).containsExactly(anasAddress);
    }

    private List<UUID> ids(Actor actor, String query, UUID argument) {
        return rls.as(actor, sql -> sql.queryForList(query, UUID.class, argument));
    }

    private UUID address(Account customer, boolean primary) {
        UUID id = UUID.randomUUID();
        owner.update(INSERT_ADDRESS, id, customer.id.value(), primary);
        return id;
    }

    /** The accounts of the given user ids; fails on a duplicate, so a row seen twice is not hidden by the set. */
    private static Set<Account> accounts(List<UUID> userIds) {
        assertThat(userIds).doesNotHaveDuplicates();
        return userIds.stream()
            .map(id -> Stream.of(Account.values()).filter(a -> a.id.value().equals(id)).findFirst().orElseThrow())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Account.class)));
    }
}
