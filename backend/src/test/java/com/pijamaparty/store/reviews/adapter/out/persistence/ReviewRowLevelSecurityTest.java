package com.pijamaparty.store.reviews.adapter.out.persistence;

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
 * T-E5-10: the Row Level Security policies of {@code review} (V37, RNF-27), tested for reads, inserts, updates and
 * deletes with customer A, customer B, a guest, the administrator, the system and a missing context. Everyone but the
 * system reads the approved reviews; a customer also reads all of its own, submits its own as pending, edits them
 * back to pending and deletes them only once its account is gone (UC-28); the administrator reads every review,
 * moderates and deletes it; the system and a missing context reach nothing. Each caller also reaches for every review
 * by its id, as a query that forgot to filter by customer would. Which columns each actor may change is
 * {@link ReviewsSchemaTest}. Every transaction is committed by the application role.
 */
@ApiTest
class ReviewRowLevelSecurityTest {

    private static final String INSERT_REVIEW = """
        insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                            moderated_at)
        values (?, ?, ?, 4, null, 'Ana', ?, now(), now(), ?::timestamptz)
        """;
    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, '{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                'Ana', 'CUSTOMER', 0, now(), now())
        """;
    private static final String EDIT = """
        update review set rating = 1, status = 'PENDING', moderated_at = null, updated_at = now() where id = ?
        """;
    private static final String MODERATE = "update review set status = 'REJECTED', moderated_at = now() where id = ?";
    private static final String RLS_VIOLATION = "row-level security";
    private static final String ADMIN_GUARD = "the administrator may only update the status of a review";

    /** The seeded reviews: each customer has one in every status, each of a different product. */
    enum Review {
        A_PENDING(CUSTOMER_A_ID, "PENDING"),
        A_APPROVED(CUSTOMER_A_ID, "APPROVED"),
        A_REJECTED(CUSTOMER_A_ID, "REJECTED"),
        B_PENDING(CUSTOMER_B_ID, "PENDING"),
        B_APPROVED(CUSTOMER_B_ID, "APPROVED"),
        B_REJECTED(CUSTOMER_B_ID, "REJECTED");

        private final UserId author;
        private final String status;

        Review(UserId author, String status) {
            this.author = author;
            this.status = status;
        }

        static Set<Review> of(UserId author) {
            return Stream.of(values()).filter(r -> r.author.equals(author))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Review.class)));
        }
    }

    private static final Set<Review> ALL = EnumSet.allOf(Review.class);
    private static final Set<Review> APPROVED = EnumSet.of(Review.A_APPROVED, Review.B_APPROVED);
    private static final Set<Review> NONE = EnumSet.noneOf(Review.class);

    /**
     * Who runs the transaction ({@code actor} empty: no security context), the reviews it reads, the customer it
     * submits reviews for (if any), the reviews it edits back to pending and those it moderates and deletes.
     */
    record Caller(String name, Optional<Actor> actor, Set<Review> reads, Optional<UserId> submitsFor,
                  Set<Review> edits, Set<Review> moderates) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A), union(Review.of(CUSTOMER_A_ID), APPROVED),
                Optional.of(CUSTOMER_A_ID), Review.of(CUSTOMER_A_ID), NONE),
            new Caller("customer B", Optional.of(CUSTOMER_B), union(Review.of(CUSTOMER_B_ID), APPROVED),
                Optional.of(CUSTOMER_B_ID), Review.of(CUSTOMER_B_ID), NONE),
            new Caller("guest", Optional.of(GUEST), APPROVED, Optional.empty(), NONE, NONE),
            new Caller("administrator", Optional.of(ADMIN), ALL, Optional.empty(), NONE, ALL),
            new Caller("system", Optional.of(SYSTEM), NONE, Optional.empty(), NONE, NONE),
            new Caller("no context", Optional.empty(), NONE, Optional.empty(), NONE, NONE));
    }

    private static Set<Review> union(Set<Review> first, Set<Review> second) {
        Set<Review> union = EnumSet.noneOf(Review.class);
        union.addAll(first);
        union.addAll(second);
        return union;
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final Map<Review, UUID> reviews = new EnumMap<>(Review.class);

    ReviewRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                               PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Both customers have an account and a review in every status, stored by the owner role (no policies). */
    @BeforeEach
    void seedReviews() {
        owner.update(INSERT_USER, CUSTOMER_A_ID.value(), "a@example.com");
        owner.update(INSERT_USER, CUSTOMER_B_ID.value(), "b@example.com");
        for (Review review : Review.values()) {
            var id = UUID.randomUUID();
            owner.update(INSERT_REVIEW, id, UUID.randomUUID(), review.author.value(), review.status,
                "PENDING".equals(review.status) ? null : "2026-10-17T12:00:00Z");
            reviews.put(review, id);
        }
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review, address, user_token, external_identity, pending_link, admin_recovery_code, "
            + "app_user");
    }

    @Test
    void givenTheReviewTable_whenCheckingItsProtection_thenRowLevelSecurityIsEnabledAndForced() {
        var flags = owner.queryForMap(
            "select relrowsecurity, relforcerowsecurity from pg_class where oid = 'review'::regclass");

        assertThat(flags).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAReviewInEveryStatus_whenReading_thenOnlyTheReachableOnesAreVisible(Caller caller) {
        var visible = caller.run(rls, sql -> sql.queryForList("select id from review", UUID.class));

        assertThat(seeded(visible)).isEqualTo(caller.reads());
        for (Review review : Review.values()) {
            var byId = caller.run(rls, sql -> sql.queryForList("select id from review where id = ?", UUID.class,
                reviews.get(review)));
            assertThat(byId).as(review.name()).hasSize(caller.reads().contains(review) ? 1 : 0);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAProductNotReviewed_whenSubmittingAPendingReviewForEveryUser_thenOnlyItsOwnIsAccepted(Caller caller) {
        for (UserId author : List.of(CUSTOMER_A_ID, CUSTOMER_B_ID, ADMIN_ID)) {
            var submit = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_REVIEW, UUID.randomUUID(),
                UUID.randomUUID(), author.value(), "PENDING", null);
            if (caller.submitsFor().filter(author::equals).isPresent()) {
                assertThat(caller.run(rls, submit)).as(author.toString()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, submit)).as(author.toString())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAReviewInEveryStatus_whenEditingEachBackToPending_thenOnlyTheOwnChange(Caller caller) {
        for (Review review : Review.values()) {
            var edit = (Function<JdbcTemplate, Integer>) sql -> sql.update(EDIT, reviews.get(review));
            if (caller.moderates().contains(review)) {
                assertThatThrownBy(() -> caller.run(rls, edit)).as(review.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(ADMIN_GUARD);
            } else {
                assertThat(caller.run(rls, edit)).as(review.name()).isEqualTo(caller.edits().contains(review) ? 1 : 0);
            }
        }

        assertThat(seeded(owner.queryForList("select id from review where rating = 1", UUID.class)))
            .isEqualTo(caller.edits());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAReviewInEveryStatus_whenRejectingEach_thenOnlyTheAdministratorModeratesThem(Caller caller) {
        for (Review review : Review.values()) {
            var moderate = (Function<JdbcTemplate, Integer>) sql -> sql.update(MODERATE, reviews.get(review));
            if (caller.edits().contains(review)) {
                assertThatThrownBy(() -> caller.run(rls, moderate)).as(review.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            } else {
                assertThat(caller.run(rls, moderate)).as(review.name())
                    .isEqualTo(caller.moderates().contains(review) ? 1 : 0);
            }
        }

        var rejected = union(caller.moderates(), EnumSet.of(Review.A_REJECTED, Review.B_REJECTED));
        assertThat(seeded(owner.queryForList("select id from review where status = 'REJECTED'", UUID.class)))
            .isEqualTo(rejected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAReviewInEveryStatusAndBothAccounts_whenDeletingAll_thenOnlyTheAdministratorDeletesThem(Caller caller) {
        var deleted = caller.run(rls, sql -> sql.queryForList("delete from review returning id", UUID.class));

        assertThat(seeded(deleted)).isEqualTo(caller.moderates());
        assertThat(seeded(owner.queryForList("select id from review", UUID.class)))
            .isEqualTo(EnumSet.complementOf(withNone(caller.moderates())));
    }

    @Test
    void givenBothAccountsDeleted_whenCustomerADeletesAllReviewsWithItsAccount_thenOnlyItsOwnGo() {
        owner.update("delete from app_user where id = ?", CUSTOMER_B_ID.value());

        var deleted = rls.as(CUSTOMER_A, sql -> {
            sql.update("delete from app_user where id = ?", CUSTOMER_A_ID.value());
            return sql.queryForList("delete from review returning id", UUID.class);
        });

        assertThat(seeded(deleted)).isEqualTo(Review.of(CUSTOMER_A_ID));
        assertThat(seeded(owner.queryForList("select id from review", UUID.class))).isEqualTo(Review.of(CUSTOMER_B_ID));
    }

    @Test
    void givenAnOwnReview_whenTheCustomerHandsItToAnotherCustomer_thenItIsRejected() {
        var review = reviews.get(Review.A_PENDING);

        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update("update review set user_id = ? where id = ?",
                CUSTOMER_B_ID.value(), review)))
            .isInstanceOf(DataAccessException.class);
        assertThat(owner.queryForObject("select user_id from review where id = ?", UUID.class, review))
            .isEqualTo(CUSTOMER_A_ID.value());
    }

    /** The seeded reviews of the given ids; fails on a duplicate, so a review seen twice is not hidden by the set. */
    private Set<Review> seeded(List<UUID> ids) {
        assertThat(ids).doesNotHaveDuplicates();
        return ids.stream()
            .map(id -> reviews.entrySet().stream().filter(e -> e.getValue().equals(id)).findFirst().orElseThrow()
                .getKey())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Review.class)));
    }

    /** EnumSet.copyOf refuses an empty plain collection; this keeps the element type for it. */
    private static EnumSet<Review> withNone(Set<Review> seeded) {
        return seeded.isEmpty() ? EnumSet.noneOf(Review.class) : EnumSet.copyOf(seeded);
    }
}
