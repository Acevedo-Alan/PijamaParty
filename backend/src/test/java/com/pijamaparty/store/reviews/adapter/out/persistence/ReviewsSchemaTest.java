package com.pijamaparty.store.reviews.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E5-02: the review schema (V37) enforces the UC-24 and UC-25 rules and guards the columns each actor may change
 * (RNF-27), as committed transactions of the application role. Which rows each actor reaches, with the five actors,
 * is T-E5-10.
 */
@ApiTest
class ReviewsSchemaTest {

    private static final String INSERT_REVIEW = """
        insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                            moderated_at)
        values (?, ?, ?, ?, ?, ?, ?, now(), now(), ?::timestamptz)
        """;
    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, '{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                'Ana', 'CUSTOMER', 0, now(), now())
        """;
    private static final UUID PRODUCT = UUID.randomUUID();

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    ReviewsSchemaTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                      PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review, address, user_token, external_identity, pending_link, admin_recovery_code, "
            + "app_user");
    }

    // --- constraints ---

    @Test
    void givenACustomer_whenItSubmitsAPendingReview_thenItIsAccepted() {
        int inserted = rls.as(CUSTOMER_A, sql -> sql.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT,
            CUSTOMER_A_ID.value(), 5, "Muy cómodo", "Ana", "PENDING", null));

        assertThat(inserted).isOne();
    }

    @Test
    void givenAReviewOfTheProduct_whenTheSameCustomerSubmitsAnother_thenItIsRejected() {
        review(CUSTOMER_A_ID.value(), "PENDING");

        assertThatThrownBy(() -> review(CUSTOMER_A_ID.value(), "PENDING"))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("review_user_product_uk");
    }

    @Test
    void givenARatingOutside1To5_whenStored_thenItIsRejected() {
        for (int rating : new int[] {0, 6}) {
            assertThatThrownBy(() -> owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(),
                    rating, null, "Ana", "PENDING", null))
                .as("rating " + rating).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("review_rating_ck");
        }
    }

    @Test
    void givenATextBlankUntrimmedOrTooLong_whenStored_thenItIsRejected() {
        owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(), 4, "x".repeat(1000), "Ana",
            "PENDING", null);

        for (String text : List.of("", " Lindo", "Lindo ", "x".repeat(1001))) {
            assertThatThrownBy(() -> owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(), 4,
                    text, "Ana", "PENDING", null))
                .as("[" + text + "]").isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("review_text_ck");
        }
    }

    @Test
    void givenAnAuthorNameBlankUntrimmedOrTooLong_whenStored_thenItIsRejected() {
        for (String name : List.of("", " Ana", "x".repeat(101))) {
            assertThatThrownBy(() -> owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(), 4,
                    null, name, "PENDING", null))
                .as("[" + name + "]").isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("review_author_name_ck");
        }
    }

    @Test
    void givenAStatusAndAModerationTimeThatDisagree_whenStored_thenItIsRejected() {
        assertThatThrownBy(() -> owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(), 4, null,
                "Ana", "HIDDEN", "2026-10-17T12:00:00Z"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("review_status_ck");
        assertThatThrownBy(() -> owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(), 4, null,
                "Ana", "PENDING", "2026-10-17T12:00:00Z"))
            .as("pending, moderated").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("review_moderated_at_ck");
        assertThatThrownBy(() -> owner.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT, UUID.randomUUID(), 4, null,
                "Ana", "APPROVED", null))
            .as("approved, not moderated").isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("review_moderated_at_ck");
    }

    // --- policies and column guards ---

    @Test
    void givenACustomer_whenItSubmitsAReviewAlreadyApprovedOrOfAnotherCustomer_thenItIsRejected() {
        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT,
                CUSTOMER_A_ID.value(), 5, null, "Ana", "APPROVED", "2026-10-17T12:00:00Z")))
            .as("approved").isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(INSERT_REVIEW, UUID.randomUUID(), PRODUCT,
                CUSTOMER_B_ID.value(), 5, null, "Ana", "PENDING", null)))
            .as("of customer B").isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenReviewsInEveryStatus_whenRead_thenTheGuestSeesTheApprovedOnesAndTheAuthorAlsoItsOwn() {
        UUID approved = review(CUSTOMER_B_ID.value(), "APPROVED");
        UUID pendingOfA = review(CUSTOMER_A_ID.value(), "PENDING");
        review(UUID.randomUUID(), "REJECTED");
        String select = "select id from review";

        List<UUID> guest = rls.as(GUEST, sql -> sql.queryForList(select, UUID.class));
        List<UUID> author = rls.as(CUSTOMER_A, sql -> sql.queryForList(select, UUID.class));
        List<UUID> admin = rls.as(ADMIN, sql -> sql.queryForList(select, UUID.class));

        assertThat(guest).containsExactly(approved);
        assertThat(author).containsExactlyInAnyOrder(approved, pendingOfA);
        assertThat(admin).hasSize(3);
    }

    @Test
    void givenAnApprovedReview_whenItsAuthorEditsIt_thenItGoesBackToPending() {
        UUID review = review(CUSTOMER_A_ID.value(), "APPROVED");

        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(
                "update review set rating = 3, updated_at = now() where id = ?", review)))
            .as("still approved").isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");

        int edited = rls.as(CUSTOMER_A, sql -> sql.update("""
            update review set rating = 3, text = 'Achica al lavar', author_name = 'Ana', status = 'PENDING',
                              moderated_at = null, updated_at = now()
            where id = ?
            """, review));

        assertThat(edited).isOne();
        assertThat(owner.queryForObject("select status from review where id = ?", String.class, review))
            .isEqualTo("PENDING");
    }

    @Test
    void givenACustomer_whenItApprovesOrMovesItsReview_thenItIsRejected() {
        UUID review = review(CUSTOMER_A_ID.value(), "PENDING");

        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(
                "update review set status = 'APPROVED', moderated_at = now() where id = ?", review)))
            .as("approve").isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        for (String update : List.of(
                "update review set product_id = gen_random_uuid() where id = ?",
                "update review set created_at = now() - interval '1 day' where id = ?")) {
            assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(update, review)))
                .as(update).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("a customer may only update the rating, text and author name of its review");
        }
        int ofCustomerB = rls.as(CUSTOMER_B, sql -> sql.update("update review set rating = 1 where id = ?", review));
        assertThat(ofCustomerB).as("customer B").isZero();
    }

    @Test
    void givenAPendingReview_whenTheAdministratorModeratesIt_thenOnlyTheStatusChanges() {
        UUID review = review(CUSTOMER_A_ID.value(), "PENDING");

        int approved = rls.as(ADMIN, sql -> sql.update(
            "update review set status = 'APPROVED', moderated_at = now() where id = ?", review));
        int rejected = rls.as(ADMIN, sql -> sql.update(
            "update review set status = 'REJECTED', moderated_at = now() where id = ?", review));

        assertThat(approved + rejected).isEqualTo(2);
        assertThatThrownBy(() -> rls.as(ADMIN, sql -> sql.update(
                "update review set status = 'PENDING', moderated_at = null where id = ?", review)))
            .as("back to pending").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("row-level security");
        assertThatThrownBy(() -> rls.as(ADMIN, sql -> sql.update(
                "update review set text = 'Editado' where id = ?", review)))
            .as("text").isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("the administrator may only update the status of a review");
        int deleted = rls.as(ADMIN, sql -> sql.update("delete from review where id = ?", review));
        assertThat(deleted).isOne();
    }

    @Test
    void givenACustomerWithReviews_whenItDeletesThemWithItsAccount_thenOnlyThenAreTheyDeleted() {
        owner.update(INSERT_USER, CUSTOMER_A_ID.value(), "a@example.com");
        UUID review = review(CUSTOMER_A_ID.value(), "APPROVED");

        int withItsAccount = rls.as(CUSTOMER_A, sql -> sql.update("delete from review where id = ?", review));
        assertThat(withItsAccount).as("with its account").isZero();

        int deleted = rls.as(CUSTOMER_A, sql -> sql.update("delete from app_user where id = ?",
            CUSTOMER_A_ID.value()) + sql.update("delete from review where user_id = ?", CUSTOMER_A_ID.value()));

        assertThat(deleted).isEqualTo(2);
        assertThat(owner.queryForObject("select count(*) from review", Integer.class)).isZero();
    }

    private UUID review(UUID userId, String status) {
        var id = UUID.randomUUID();
        owner.update(INSERT_REVIEW, id, PRODUCT, userId, 4, null, "Ana", status,
            "PENDING".equals(status) ? null : "2026-10-17T12:00:00Z");
        return id;
    }
}
