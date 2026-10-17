package com.pijamaparty.store.accounts.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
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
 * T-E4-01: the customer account schema (V29): the phone of app_user, the address book, the single-use tokens of the
 * email links and the column guards of app_user and user_token (RNF-27), as committed transactions of the application
 * role. Which rows each actor reaches, with the five actors, is {@link AccountRowLevelSecurityTest} (T-E4-11).
 */
@ApiTest
class AccountSchemaTest {

    private static final String PASSWORD = "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_PASSWORD = "{bcrypt}$2a$12$bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
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
        values (?, ?, ?, ?, now(), now() + ?::interval)
        """;
    private static final String VERIFICATION_HASH = "a".repeat(64);
    private static final String RESET_HASH = "b".repeat(64);

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    AccountSchemaTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                      PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void seedUsers() {
        owner.update(INSERT_USER, CUSTOMER_A_ID.value(), "a@example.com", false, PASSWORD, "CUSTOMER");
        owner.update(INSERT_USER, CUSTOMER_B_ID.value(), "b@example.com", false, PASSWORD, "CUSTOMER");
        owner.update(INSERT_USER, ADMIN_ID.value(), "owner@example.com", true, PASSWORD, "ADMIN");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate address, user_token, admin_recovery_code, app_user");
    }

    // --- app_user.phone ---

    @Test
    void givenAPhone_whenStored_thenOnlyEightToFifteenDigitsAreAccepted() {
        owner.update("update app_user set phone = '3425551234' where id = ?", CUSTOMER_A_ID.value());

        for (String phone : List.of("1234567", "1234567890123456", "342 555 1234", "+543425551234")) {
            assertThatThrownBy(() -> owner.update("update app_user set phone = ? where id = ?", phone,
                    CUSTOMER_A_ID.value()))
                .as(phone).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("app_user_phone_ck");
        }
    }

    // --- address ---

    @Test
    void givenAPrimaryAddress_whenAnotherOneIsMarkedPrimary_thenItIsRejectedUntilTheFirstIsUnmarked() {
        UUID first = address(CUSTOMER_A_ID.value(), true);
        UUID second = address(CUSTOMER_A_ID.value(), false);
        address(CUSTOMER_B_ID.value(), true);

        assertThatThrownBy(() -> owner.update("update address set is_primary = true where id = ?", second))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("address_primary_uk");

        // A partial unique index cannot be deferred: the previous primary is unmarked first.
        int swapped = rls.as(CUSTOMER_A, sql -> sql.update("update address set is_primary = false where id = ?", first)
            + sql.update("update address set is_primary = true where id = ?", second));
        assertThat(swapped).isEqualTo(2);
        assertThat(owner.queryForObject("select is_primary from address where id = ?", Boolean.class, first))
            .isFalse();
    }

    @Test
    void givenAnAddressWithFieldsOutOfFormat_whenStored_thenTheChecksRejectIt() {
        UUID id = address(CUSTOMER_A_ID.value(), true);

        for (var update : List.of(
                List.of("street = ''", "address_street_ck"),
                List.of("street = ' San Martín'", "address_street_ck"),
                List.of("street_number = '12345678901'", "address_street_number_ck"),
                List.of("floor = ''", "address_floor_ck"),
                List.of("apartment = '12345678901'", "address_apartment_ck"),
                List.of("city = ''", "address_city_ck"),
                List.of("province = 'SANTA FE'", "address_province_ck"),
                List.of("postal_code = 's3000abc'", "address_postal_code_ck"),
                List.of("postal_code = '300'", "address_postal_code_ck"))) {
            assertThatThrownBy(() -> owner.update("update address set " + update.get(0) + " where id = ?", id))
                .as(update.get(0)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(update.get(1));
        }
    }

    @Test
    void givenACustomerWithAddressesAndTokens_whenItsUserIsDeleted_thenTheyGoWithIt() {
        address(CUSTOMER_A_ID.value(), true);
        token(CUSTOMER_A_ID.value(), "EMAIL_VERIFICATION", VERIFICATION_HASH, "24 hours");

        owner.update("delete from app_user where id = ?", CUSTOMER_A_ID.value());

        assertThat(owner.queryForObject("select count(*) from address", Integer.class)).isZero();
        assertThat(owner.queryForObject("select count(*) from user_token", Integer.class)).isZero();
    }

    // --- user_token ---

    @Test
    void givenATokenPurpose_whenStoredWithAnotherLifetime_thenItIsRejected() {
        for (var token : List.of(List.of("EMAIL_VERIFICATION", "30 minutes"), List.of("PASSWORD_RESET", "24 hours"),
                List.of("EMAIL_VERIFICATION", "25 hours"))) {
            assertThatThrownBy(() -> token(CUSTOMER_A_ID.value(), token.get(0), VERIFICATION_HASH, token.get(1)))
                .as(token.toString()).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_token_expiry_ck");
        }
        // Until T-E4-13 both the purpose and the expiry checks refuse it; PostgreSQL reports one of them.
        assertThatThrownBy(() -> token(CUSTOMER_A_ID.value(), "LINK_CONFIRMATION", VERIFICATION_HASH, "24 hours"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageMatching("(?s).*user_token_(purpose|expiry)_ck.*");
        assertThatThrownBy(() -> token(CUSTOMER_A_ID.value(), "PASSWORD_RESET", "B".repeat(64), "30 minutes"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_token_hash_format_ck");
    }

    @Test
    void givenATokenHash_whenStoredTwice_thenItIsRejected() {
        token(CUSTOMER_A_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes");

        assertThatThrownBy(() -> token(CUSTOMER_B_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes"))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("user_token_hash_uk");
    }

    @Test
    void givenAToken_whenUsedAfterItsExpiry_thenItIsRejected() {
        token(CUSTOMER_A_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes");

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(
                "update user_token set used_at = expires_at where token_hash = ?", RESET_HASH)))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_token_used_ck");
    }

    @Test
    void givenAUsedToken_whenTheSystemChangesItAgainOrChangesAnythingElse_thenItIsRejected() {
        token(CUSTOMER_A_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes");
        rls.as(SYSTEM, sql -> sql.update("update user_token set used_at = now() where token_hash = ?", RESET_HASH));

        for (String update : List.of(
                "update user_token set used_at = null where token_hash = ?",
                "update user_token set used_at = used_at + interval '1 second' where token_hash = ?",
                "update user_token set user_id = '" + CUSTOMER_B_ID.value() + "' where token_hash = ?")) {
            assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(update, RESET_HASH)))
                .as(update).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("a user token only changes from unused to used");
        }
    }

    // --- app_user: registration and token-bound changes ---

    @Test
    void givenTheSystem_whenRegisteringACustomer_thenOnlyAnUnverifiedOneWithAPasswordIsAllowed() {
        int registered = rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(), "new@example.com", false,
            PASSWORD, "CUSTOMER"));
        assertThat(registered).isOne();

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(),
                "verified@example.com", true, PASSWORD, "CUSTOMER")))
            .as("already verified").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("row-level security");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(),
                "nopassword@example.com", false, null, "CUSTOMER")))
            .as("without a password").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("row-level security");
    }

    @Test
    void givenAConsumedVerificationTokenOfTheUser_whenTheSystemVerifiesItsEmail_thenItIsAllowed() {
        token(CUSTOMER_A_ID.value(), "EMAIL_VERIFICATION", VERIFICATION_HASH, "24 hours");

        int verified = rls.as(SYSTEM, sql -> {
            bindAccountToken(sql, VERIFICATION_HASH);
            sql.update("update user_token set used_at = now() where token_hash = ?", VERIFICATION_HASH);
            return sql.update("update app_user set email_verified = true, updated_at = now() where id = ?",
                CUSTOMER_A_ID.value());
        });

        assertThat(verified).isOne();
    }

    @Test
    void givenNoConsumedVerificationTokenOfTheUser_whenTheSystemVerifiesAnEmail_thenItIsRejected() {
        token(CUSTOMER_A_ID.value(), "EMAIL_VERIFICATION", VERIFICATION_HASH, "24 hours");
        token(CUSTOMER_B_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes");
        String verify = "update app_user set email_verified = true where id = ?";

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(verify, CUSTOMER_A_ID.value())))
            .as("without a token in the context").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> {
                bindAccountToken(sql, VERIFICATION_HASH);
                return sql.update(verify, CUSTOMER_A_ID.value());
            }))
            .as("with the token unused").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> {
                bindAccountToken(sql, VERIFICATION_HASH);
                sql.update("update user_token set used_at = now() where token_hash = ?", VERIFICATION_HASH);
                return sql.update(verify, CUSTOMER_B_ID.value());
            }))
            .as("with another user's token").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> {
                bindAccountToken(sql, RESET_HASH);
                sql.update("update user_token set used_at = now() where token_hash = ?", RESET_HASH);
                return sql.update(verify, CUSTOMER_A_ID.value());
            }))
            .as("with another user's reset token").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
    }

    @Test
    void givenAConsumedResetTokenOfTheUser_whenTheSystemVerifiesItsEmail_thenItIsAllowed() {
        token(CUSTOMER_A_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes");

        int verified = rls.as(SYSTEM, sql -> {
            bindAccountToken(sql, RESET_HASH);
            sql.update("update user_token set used_at = now() where token_hash = ?", RESET_HASH);
            return sql.update("update app_user set email_verified = true, updated_at = now() where id = ?",
                CUSTOMER_A_ID.value());
        });

        assertThat(verified).as("V31: opening the reset link proves the inbox").isOne();
    }

    @Test
    void givenAConsumedResetTokenOfTheCustomer_whenTheSystemReplacesItsPassword_thenOnlyThatIsAllowed() {
        token(CUSTOMER_A_ID.value(), "PASSWORD_RESET", RESET_HASH, "30 minutes");
        String reset = "update app_user set password_hash = ?, updated_at = now() where id = ?";

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(reset, OTHER_PASSWORD, CUSTOMER_A_ID.value())))
            .as("without a token in the context").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");

        int replaced = rls.as(SYSTEM, sql -> {
            bindAccountToken(sql, RESET_HASH);
            sql.update("update user_token set used_at = now() where token_hash = ?", RESET_HASH);
            return sql.update(reset, OTHER_PASSWORD, CUSTOMER_A_ID.value());
        });
        assertThat(replaced).isOne();

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> {
                bindAccountToken(sql, RESET_HASH);
                return sql.update("update app_user set email = 'evil@example.com' where id = ?",
                    CUSTOMER_A_ID.value());
            }))
            .as("the email, even with the token").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
    }

    @Test
    void givenACustomer_whenUpdatingItsNamePhoneAndPassword_thenItIsAllowed() {
        int updated = rls.as(CUSTOMER_A, sql -> sql.update("""
            update app_user set name = 'Ana', phone = '3425551234', password_hash = ?, updated_at = now()
            where id = ?
            """, OTHER_PASSWORD, CUSTOMER_A_ID.value()));

        assertThat(updated).isOne();
    }

    @Test
    void givenACustomer_whenChangingAnythingElseOfItsUser_thenItIsRejected() {
        for (String update : List.of(
                "update app_user set email_verified = true where id = ?",
                "update app_user set email = 'other@example.com' where id = ?",
                "update app_user set failed_login_attempts = 0, locked_until = now() where id = ?",
                "update app_user set password_hash = null where id = ?",
                "update app_user set created_at = now() - interval '1 day' where id = ?")) {
            assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(update, CUSTOMER_A_ID.value())))
                .as(update).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("a customer may only update its name, phone and password");
        }
    }

    private UUID address(UUID userId, boolean primary) {
        UUID id = UUID.randomUUID();
        owner.update(INSERT_ADDRESS, id, userId, primary);
        return id;
    }

    private void token(UUID userId, String purpose, String hash, String lifetime) {
        owner.update(INSERT_TOKEN, UUID.randomUUID(), userId, purpose, hash, lifetime);
    }

    private static void bindAccountToken(JdbcTemplate sql, String hash) {
        sql.queryForObject("select set_config('app.account_token_hash', ?, true)", String.class, hash);
    }
}
