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
 * role. T-E4-13 (V34): the linked external accounts, the link confirmation and the social sign-in changes of
 * app_user. T-E4-14 (V35): the external account waiting for its link confirmation email, and that email's type. Which
 * rows each actor reaches, with the five actors, is {@link AccountRowLevelSecurityTest} (T-E4-11).
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
    private static final String INSERT_LINK_TOKEN = """
        insert into user_token (id, user_id, purpose, token_hash, created_at, expires_at, provider, subject)
        values (?, ?, 'LINK_CONFIRMATION', ?, now(), now() + ?::interval, ?, ?)
        """;
    private static final String INSERT_IDENTITY = """
        insert into external_identity (id, user_id, provider, subject, email, created_at)
        values (?, ?, ?, ?, ?, now())
        """;
    private static final String VERIFICATION_HASH = "a".repeat(64);
    private static final String RESET_HASH = "b".repeat(64);
    private static final String LINK_HASH = "c".repeat(64);
    private static final String VERIFY_AND_DROP_PASSWORD = """
        update app_user set email_verified = true, password_hash = null, updated_at = now() where id = ?
        """;

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
        owner.execute("truncate email_outbox, address, user_token, external_identity, pending_link, admin_recovery_code, "
            + "app_user");
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
    void givenACustomerWithAddressesTokensAndExternalAccounts_whenItsUserIsDeleted_thenTheyGoWithIt() {
        address(CUSTOMER_A_ID.value(), true);
        token(CUSTOMER_A_ID.value(), "EMAIL_VERIFICATION", VERIFICATION_HASH, "24 hours");
        identity(CUSTOMER_A_ID.value(), "GOOGLE", "google-a", "a@example.com");

        owner.update("delete from app_user where id = ?", CUSTOMER_A_ID.value());

        assertThat(owner.queryForObject("select count(*) from address", Integer.class)).isZero();
        assertThat(owner.queryForObject("select count(*) from user_token", Integer.class)).isZero();
        assertThat(owner.queryForObject("select count(*) from external_identity", Integer.class)).isZero();
    }

    // --- pending_link (T-E4-14, V35) ---

    @Test
    void givenACustomerWithAPendingLink_whenAnotherIsStoredOrItsUserDeleted_thenThereIsOneAtMostAndItGoesWithIt() {
        pendingLink(CUSTOMER_A_ID.value(), "FACEBOOK", "facebook-a");

        assertThatThrownBy(() -> pendingLink(CUSTOMER_A_ID.value(), "FACEBOOK", "facebook-a2"))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("pending_link_pk");

        owner.update("delete from app_user where id = ?", CUSTOMER_A_ID.value());
        assertThat(owner.queryForObject("select count(*) from pending_link", Integer.class)).isZero();
    }

    @Test
    void givenAPendingLinkWithFieldsOutOfFormat_whenStored_thenTheChecksRejectIt() {
        for (var link : List.of(
                List.of("APPLE", "123", "pending_link_provider_ck"),
                List.of("facebook", "123", "pending_link_provider_ck"),
                List.of("FACEBOOK", "", "pending_link_subject_ck"),
                List.of("FACEBOOK", "12 3", "pending_link_subject_ck"),
                List.of("FACEBOOK", "1".repeat(256), "pending_link_subject_ck"))) {
            assertThatThrownBy(() -> pendingLink(CUSTOMER_A_ID.value(), link.get(0), link.get(1)))
                .as(link.toString()).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(link.get(2));
        }
    }

    @Test
    void givenTheLinkConfirmationEmail_whenEnqueued_thenTheOutboxAcceptsItsType() {
        owner.update("""
            insert into email_outbox (id, type, reference, version, status, attempts, next_attempt_at, created_at,
                                      updated_at)
            values (?, 'LINK_CONFIRMATION', ?, 1, 'PENDING', 0, now(), now(), now())
            """, UUID.randomUUID(), CUSTOMER_A_ID.value());

        assertThat(owner.queryForObject("select count(*) from email_outbox", Integer.class)).isOne();
    }

    // --- external_identity ---

    @Test
    void givenALinkedExternalAccount_whenLinkedAgainToAnyoneOrASecondOneOfTheProvider_thenItIsRejected() {
        identity(CUSTOMER_A_ID.value(), "GOOGLE", "google-a", "a@example.com");

        assertThatThrownBy(() -> identity(CUSTOMER_B_ID.value(), "GOOGLE", "google-a", "b@example.com"))
            .as("to another customer").isInstanceOf(DuplicateKeyException.class)
            .hasMessageContaining("external_identity_subject_uk");
        assertThatThrownBy(() -> identity(CUSTOMER_A_ID.value(), "GOOGLE", "google-a2", "a@example.com"))
            .as("a second Google account").isInstanceOf(DuplicateKeyException.class)
            .hasMessageContaining("external_identity_user_provider_uk");

        // The same subject at another provider is another account.
        identity(CUSTOMER_A_ID.value(), "FACEBOOK", "google-a", "a@example.com");
        assertThat(owner.queryForObject("select count(*) from external_identity", Integer.class)).isEqualTo(2);
    }

    @Test
    void givenAnExternalAccountWithFieldsOutOfFormat_whenStored_thenTheChecksRejectIt() {
        for (var identity : List.of(
                List.of("APPLE", "123", "a@example.com", "external_identity_provider_ck"),
                List.of("google", "123", "a@example.com", "external_identity_provider_ck"),
                List.of("GOOGLE", "", "a@example.com", "external_identity_subject_ck"),
                List.of("GOOGLE", "12 3", "a@example.com", "external_identity_subject_ck"),
                List.of("GOOGLE", "1".repeat(256), "a@example.com", "external_identity_subject_ck"),
                List.of("GOOGLE", "123", "A@example.com", "external_identity_email_ck"),
                List.of("GOOGLE", "123", " a@example.com", "external_identity_email_ck"),
                List.of("GOOGLE", "123", "not-an-email", "external_identity_email_ck"))) {
            assertThatThrownBy(() -> identity(CUSTOMER_A_ID.value(), identity.get(0), identity.get(1),
                    identity.get(2)))
                .as(identity.toString()).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(identity.get(3));
        }
    }

    @Test
    void givenTheSystem_whenLinkingAnExternalAccountToTheAdministrator_thenItIsRejected() {
        int linked = rls.as(SYSTEM, sql -> sql.update(INSERT_IDENTITY, UUID.randomUUID(), CUSTOMER_A_ID.value(),
            "GOOGLE", "google-a", "a@example.com"));
        assertThat(linked).isOne();

        // R-14: the administrator signs in with a password and a second factor only.
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT_IDENTITY, UUID.randomUUID(),
                ADMIN_ID.value(), "GOOGLE", "google-owner", "owner@example.com")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenALinkedExternalAccount_whenAnyoneUpdatesIt_thenNothingChanges() {
        identity(CUSTOMER_A_ID.value(), "GOOGLE", "google-a", "a@example.com");
        String move = "update external_identity set user_id = ?";

        int bySystem = rls.as(SYSTEM, sql -> sql.update(move, CUSTOMER_B_ID.value()));
        int byOwner = rls.as(CUSTOMER_A, sql -> sql.update(move, CUSTOMER_B_ID.value()));

        assertThat(List.of(bySystem, byOwner)).containsOnly(0);
        assertThat(owner.queryForObject("select user_id from external_identity", UUID.class))
            .isEqualTo(CUSTOMER_A_ID.value());
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
        assertThatThrownBy(() -> linkToken(CUSTOMER_A_ID.value(), LINK_HASH, "30 minutes", "FACEBOOK", "1234"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_token_expiry_ck");
        assertThatThrownBy(() -> token(CUSTOMER_A_ID.value(), "MAGIC_LINK", VERIFICATION_HASH, "24 hours"))
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

    @Test
    void givenALinkConfirmation_whenStored_thenItLastsADayAndAlwaysNamesTheExternalAccount() {
        linkToken(CUSTOMER_A_ID.value(), LINK_HASH, "24 hours", "FACEBOOK", "1234");

        assertThatThrownBy(() -> linkToken(CUSTOMER_A_ID.value(), "d".repeat(64), "24 hours", null, null))
            .as("without the external account").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("user_token_external_account_ck");
        assertThatThrownBy(() -> linkToken(CUSTOMER_A_ID.value(), "d".repeat(64), "24 hours", "FACEBOOK", null))
            .as("without the subject").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("user_token_external_account_ck");
        assertThatThrownBy(() -> linkToken(CUSTOMER_A_ID.value(), "d".repeat(64), "24 hours", "TWITTER", "1234"))
            .as("another provider").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("user_token_provider_ck");
        assertThatThrownBy(() -> linkToken(CUSTOMER_A_ID.value(), "d".repeat(64), "24 hours", "FACEBOOK", "12 34"))
            .as("a subject out of format").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("user_token_subject_ck");
        assertThatThrownBy(() -> owner.update("""
                insert into user_token (id, user_id, purpose, token_hash, created_at, expires_at, provider, subject)
                values (?, ?, 'EMAIL_VERIFICATION', ?, now(), now() + interval '24 hours', 'FACEBOOK', '1234')
                """, UUID.randomUUID(), CUSTOMER_A_ID.value(), "d".repeat(64)))
            .as("another purpose naming an external account").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("user_token_external_account_ck");
    }

    @Test
    void givenAnUnusedLinkConfirmation_whenTheSystemChangesItsExternalAccount_thenItIsRejected() {
        linkToken(CUSTOMER_A_ID.value(), LINK_HASH, "24 hours", "FACEBOOK", "1234");

        for (String update : List.of(
                "update user_token set used_at = now(), subject = '5678' where token_hash = ?",
                "update user_token set used_at = now(), provider = 'GOOGLE' where token_hash = ?")) {
            assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(update, LINK_HASH)))
                .as(update).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("a user token only changes from unused to used");
        }
    }

    // --- app_user: registration and token-bound changes ---

    @Test
    void givenAConsumedLinkConfirmationOfTheCustomer_whenTheSystemVerifiesItAndDropsItsPassword_thenItIsAllowed() {
        linkToken(CUSTOMER_A_ID.value(), LINK_HASH, "24 hours", "FACEBOOK", "1234");

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(VERIFY_AND_DROP_PASSWORD, CUSTOMER_A_ID.value())))
            .as("without the token").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> {
                bindAccountToken(sql, LINK_HASH);
                sql.update("update user_token set used_at = now() where token_hash = ?", LINK_HASH);
                return sql.update(VERIFY_AND_DROP_PASSWORD, CUSTOMER_B_ID.value());
            }))
            .as("another customer's").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");

        int verified = rls.as(SYSTEM, sql -> {
            bindAccountToken(sql, LINK_HASH);
            sql.update("update user_token set used_at = now() where token_hash = ?", LINK_HASH);
            sql.update(INSERT_IDENTITY, UUID.randomUUID(), CUSTOMER_A_ID.value(), "FACEBOOK", "1234",
                "a@example.com");
            return sql.update(VERIFY_AND_DROP_PASSWORD, CUSTOMER_A_ID.value());
        });

        assertThat(verified).isOne();
        assertThat(owner.queryForMap("select email_verified, password_hash from app_user where id = ?",
            CUSTOMER_A_ID.value())).containsEntry("email_verified", true).containsEntry("password_hash", null);
    }

    @Test
    void givenAGoogleAccountWithTheCustomersEmail_whenTheSystemVerifiesItAndDropsItsPassword_thenItIsAllowed() {
        identity(CUSTOMER_A_ID.value(), "GOOGLE", "google-a", "a@example.com");

        int verified = rls.as(SYSTEM, sql -> sql.update(VERIFY_AND_DROP_PASSWORD, CUSTOMER_A_ID.value()));

        assertThat(verified).isOne();
        assertThat(owner.queryForMap("select email_verified, password_hash from app_user where id = ?",
            CUSTOMER_A_ID.value())).containsEntry("email_verified", true).containsEntry("password_hash", null);
    }

    @Test
    void givenNoGoogleAccountWithTheCustomersEmail_whenTheSystemVerifiesItOrDropsItsPassword_thenItIsRejected() {
        identity(CUSTOMER_A_ID.value(), "FACEBOOK", "1234", "a@example.com");
        identity(CUSTOMER_B_ID.value(), "GOOGLE", "google-b", "other@example.com");

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(VERIFY_AND_DROP_PASSWORD, CUSTOMER_A_ID.value())))
            .as("Facebook does not vouch for the email").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(VERIFY_AND_DROP_PASSWORD, CUSTOMER_B_ID.value())))
            .as("a Google account with another email").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");

        // Linking Google to an already verified account keeps its password.
        owner.update("update app_user set email_verified = true where id = ?", CUSTOMER_B_ID.value());
        owner.update("update external_identity set email = 'b@example.com' where user_id = ?",
            CUSTOMER_B_ID.value());
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(
                "update app_user set password_hash = null where id = ?", CUSTOMER_B_ID.value())))
            .as("the password of a verified account").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("verify an email or reset a password with its token");
    }

    @Test
    void givenTheSystem_whenRegisteringACustomer_thenOnlyAVerifiedOneWithAPasswordIsRejected() {
        int withPassword = rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(), "new@example.com", false,
            PASSWORD, "CUSTOMER"));
        // V34, UC-27: Google vouches for the email; Facebook's stays unverified until it is confirmed.
        int google = rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(), "google@example.com", true,
            null, "CUSTOMER"));
        int facebook = rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(), "facebook@example.com",
            false, null, "CUSTOMER"));
        assertThat(List.of(withPassword, google, facebook)).containsOnly(1);

        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT_USER, UUID.randomUUID(),
                "verified@example.com", true, PASSWORD, "CUSTOMER")))
            .as("already verified, with a password").isInstanceOf(DataAccessException.class)
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

    private void linkToken(UUID userId, String hash, String lifetime, String provider, String subject) {
        owner.update(INSERT_LINK_TOKEN, UUID.randomUUID(), userId, hash, lifetime, provider, subject);
    }

    private void pendingLink(UUID userId, String provider, String subject) {
        owner.update("insert into pending_link (user_id, provider, subject, requested_at) values (?, ?, ?, now())",
            userId, provider, subject);
    }

    private void identity(UUID userId, String provider, String subject, String email) {
        owner.update(INSERT_IDENTITY, UUID.randomUUID(), userId, provider, subject, email);
    }

    private static void bindAccountToken(JdbcTemplate sql, String hash) {
        sql.queryForObject("select set_config('app.account_token_hash', ?, true)", String.class, hash);
    }
}
