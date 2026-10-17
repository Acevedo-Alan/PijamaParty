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
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E1-01: the app_user table (V5) and its Row Level Security policies (RNF-27), tested with customer A, customer B,
 * a guest, the administrator and the system, as committed transactions of the application role.
 */
@ApiTest
class UserSchemaTest {

    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, '{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'Test', ?, 0, now(), now())
        """;
    private static final List<Actor> ALL_ACTORS = List.of(CUSTOMER_A, CUSTOMER_B, GUEST, ADMIN, SYSTEM);

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    UserSchemaTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void seedUsers() {
        owner.update(INSERT_USER, CUSTOMER_A_ID.value(), "a@example.com", "CUSTOMER");
        owner.update(INSERT_USER, CUSTOMER_B_ID.value(), "b@example.com", "CUSTOMER");
        owner.update(INSERT_USER, ADMIN_ID.value(), "owner@example.com", "ADMIN");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
    }

    @Test
    void givenTheUserTable_whenCheckingItsProtection_thenRowLevelSecurityIsEnabledAndForced() {
        var flags = owner.queryForMap(
            "select relrowsecurity, relforcerowsecurity from pg_class where oid = 'app_user'::regclass");

        assertThat(flags).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @Test
    void givenNoSecurityContextOrAGuest_whenReadingUsers_thenNoRowIsVisible() {
        assertThat(rls.withoutContext(this::ids)).isEmpty();
        assertThat(rls.as(GUEST, this::ids)).isEmpty();
    }

    @Test
    void givenTwoCustomers_whenEachReadsUsers_thenEachSeesOnlyItself() {
        assertThat(rls.as(CUSTOMER_A, this::ids)).containsExactly(CUSTOMER_A_ID.value());
        assertThat(rls.as(CUSTOMER_B, this::ids)).containsExactly(CUSTOMER_B_ID.value());
    }

    @Test
    void givenTheAdministrator_whenReadingUsers_thenItSeesOnlyItself() {
        assertThat(rls.as(ADMIN, this::ids)).containsExactly(ADMIN_ID.value());
    }

    @Test
    void givenAnyActorButTheUserItself_whenUpdatingAnotherUsersProfile_thenNothingChanges() {
        for (Actor actor : List.of(CUSTOMER_A, GUEST, ADMIN)) {
            int updated = rls.as(actor, sql -> sql.update("update app_user set name = 'Changed' where id = ?",
                CUSTOMER_B_ID.value()));
            assertThat(updated).as(actor.role().name()).isZero();
        }
        assertThat(owner.queryForObject("select name from app_user where id = ?", String.class, CUSTOMER_B_ID.value()))
            .isEqualTo("Test");
    }

    @Test
    void givenACustomer_whenUpdatingItsOwnProfile_thenItIsAllowed() {
        int updated = rls.as(CUSTOMER_A, sql -> sql.update("update app_user set name = 'Ana' where id = ?",
            CUSTOMER_A_ID.value()));

        assertThat(updated).isOne();
    }

    @Test
    void givenEitherCustomer_whenMakingItselfAdministrator_thenItIsRejected() {
        for (Actor customer : List.of(CUSTOMER_A, CUSTOMER_B)) {
            assertThatThrownBy(() -> rls.as(customer, sql -> sql.update(
                    "update app_user set role = 'ADMIN' where id = ?", customer.userId().orElseThrow().value())))
                // Since V29 the update guard refuses it before the policy's WITH CHECK, which still backs it up.
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("a customer may only update its name, phone and password");
        }
    }

    @Test
    void givenAnyActor_whenDeletingUsers_thenNoRowIsDeleted() {
        for (Actor actor : ALL_ACTORS) {
            int deleted = rls.as(actor, sql -> sql.update("delete from app_user"));
            assertThat(deleted).as(actor.role().name()).isZero();
        }
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(3);
    }

    @Test
    void givenTheSystem_whenAuthenticating_thenItReadsUsersAndRecordsFailedAttemptsAndTheLock() {
        assertThat(rls.as(SYSTEM, this::ids)).hasSize(3);

        int updated = rls.as(SYSTEM, sql -> sql.update("""
            update app_user set failed_login_attempts = 5, locked_until = now() + interval '15 minutes',
                                updated_at = now()
            where id = ?
            """, ADMIN_ID.value()));

        assertThat(updated).isOne();
    }

    @Test
    void givenTheSystem_whenChangingAPasswordEmailOrRole_thenItIsRejected() {
        for (String update : List.of(
                "update app_user set password_hash = '{bcrypt}$2a$12$bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' where id = ?",
                "update app_user set email = 'evil@example.com' where id = ?",
                "update app_user set role = 'CUSTOMER' where id = ?",
                "update app_user set email_verified = false where id = ?",
                "update app_user set name = 'Other' where id = ?")) {
            assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(update, ADMIN_ID.value())))
                .as(update).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("the system may only update failed sign-in attempts");
        }
    }

    @Test
    void givenTheSystem_whenEnrollingMovingAndResettingTheSecondFactor_thenOnlyThoseChangesAreAllowed() {
        String enroll = """
            update app_user set totp_secret = ?, totp_enabled_at = now(), totp_last_step = ? where id = ?
            """;
        int enrolled = rls.as(SYSTEM, sql -> sql.update(enroll, new byte[] {1}, 100L, ADMIN_ID.value()));
        int moved = rls.as(SYSTEM, sql -> sql.update("update app_user set totp_last_step = 101 where id = ?",
            ADMIN_ID.value()));
        assertThat(enrolled).isOne();
        assertThat(moved).isOne();

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(enroll, new byte[] {2}, 102L, ADMIN_ID.value())))
            .as("replacing the secret without deleting it").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("a second factor is replaced only by deleting it first");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(
                "update app_user set totp_last_step = 50 where id = ?", ADMIN_ID.value())))
            .as("moving the last step back").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("the last accepted step only moves forward");

        int reset = rls.as(SYSTEM, sql -> sql.update("""
            update app_user set totp_secret = null, totp_enabled_at = null, totp_last_step = null where id = ?
            """, ADMIN_ID.value()));
        assertThat(reset).isOne();
    }

    @Test
    void givenACustomerOrAnIncompleteSecondFactor_whenStored_thenTheChecksRejectIt() {
        assertThatThrownBy(() -> owner.update(
                "update app_user set totp_secret = '\\x01', totp_enabled_at = now(), totp_last_step = 1 where id = ?",
                CUSTOMER_A_ID.value()))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("app_user_totp_admin_ck");
        assertThatThrownBy(() -> owner.update("update app_user set totp_secret = '\\x01' where id = ?",
                ADMIN_ID.value()))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("app_user_totp_complete_ck");
    }

    @Test
    void givenNoAdministrator_whenTheSystemCreatesTheInitialOne_thenItIsAllowed() {
        owner.update("delete from app_user where role = 'ADMIN'");

        int created = rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(), "new-owner@example.com", "ADMIN"));

        assertThat(created).isOne();
    }

    @Test
    void givenAnyActor_whenCreatingAnAlreadyVerifiedCustomer_thenItIsRejected() {
        // T-E4-01: the system registers customers unverified (AccountSchemaTest); Google arrives with T-E4-13.
        for (Actor actor : ALL_ACTORS) {
            assertThatThrownBy(() -> rls.as(actor, sql -> sql.update(INSERT_USER, UUID.randomUUID(),
                    "other@example.com", "CUSTOMER")))
                .as(actor.role().name()).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("row-level security");
        }
    }

    @Test
    void givenAnAdministrator_whenASecondOneIsCreated_thenItIsRejected() {
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(),
                "second@example.com", "ADMIN")))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("app_user_single_admin_uk");
    }

    @Test
    void givenAnAdministratorWithoutPassword_whenCreated_thenItIsRejected() {
        owner.update("delete from app_user where role = 'ADMIN'");

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update("""
                insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                      created_at, updated_at)
                values (?, 'owner@example.com', true, null, 'Owner', 'ADMIN', 0, now(), now())
                """, UUID.randomUUID())))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("app_user_admin_password_ck");
    }

    @Test
    void givenAnEmailInAnotherCase_whenAUserIsCreated_thenItIsRejectedUnlessNormalized() {
        owner.update("delete from app_user where role = 'ADMIN'");

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(),
                "Owner@Example.com", "ADMIN")))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("app_user_email_normalized_ck");
    }

    private List<UUID> ids(JdbcTemplate sql) {
        return sql.queryForList("select id from app_user order by email", UUID.class);
    }
}
