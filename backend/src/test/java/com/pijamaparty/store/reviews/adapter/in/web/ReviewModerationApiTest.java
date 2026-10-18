package com.pijamaparty.store.reviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-25 (T-E5-08) through the back office API, with the real security filter chain, Row Level Security and
 * PostgreSQL. The reviews are stored as the owner, in the status each test needs.
 */
@ApiTest
class ReviewModerationApiTest {

    private static final String REVIEWS = "/api/admin/reviews";
    private static final String LUNA_PAGE = "/api/catalog/products/pijama-luna/reviews";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");
    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

    private final MockMvc mvc;
    private final JdbcTemplate owner;
    private UUID product;

    ReviewModerationApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void theCatalogListsPijamaLuna() {
        UUID category = UUID.randomUUID();
        product = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', 'verano', 0, true, now(), now())
            """, category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama Luna', 'pijama-luna', '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, 'PL-M-ROSA', 'M', 0, 'Rosa', 0, 1500000, true, now(), now())
            """, UUID.randomUUID(), product);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
    }

    /** A review of Pijama Luna by a new author, last submitted at {@code submittedAt}, in {@code status}. */
    private UUID stored(String authorName, int rating, String text, String status, Instant submittedAt) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                                moderated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, product, UUID.randomUUID(), rating, text, authorName, status, Timestamp.from(submittedAt),
            Timestamp.from(submittedAt), status.equals("PENDING") ? null : Timestamp.from(submittedAt));
        return id;
    }

    private OffsetDateTime moderatedAt(UUID review) {
        return owner.queryForObject("select moderated_at from review where id = ?", OffsetDateTime.class, review);
    }

    // --- Acceptance criteria of UC-25 ---

    @Test
    void givenPendingReviews_whenTheAdministratorOpensThePanel_thenItSeesThemFromTheOldestToTheNewest()
        throws Exception {
        UUID newer = stored("Bruno", 3, null, "PENDING", T0.plusSeconds(60));
        UUID older = stored("Ana", 5, "Muy cómodo", "PENDING", T0);
        stored("Carla", 4, null, "APPROVED", T0.minusSeconds(60));

        mvc.perform(get(REVIEWS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items[*].id", contains(older.toString(), newer.toString())))
            .andExpect(jsonPath("$.items[0].product.id").value(product.toString()))
            .andExpect(jsonPath("$.items[0].product.name").value("Pijama Luna"))
            .andExpect(jsonPath("$.items[0].product.slug").value("pijama-luna"))
            .andExpect(jsonPath("$.items[0].authorName").value("Ana"))
            .andExpect(jsonPath("$.items[0].rating").value(5))
            .andExpect(jsonPath("$.items[0].text").value("Muy cómodo"))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.items[0].updatedAt").value("2026-10-18T12:00:00Z"))
            .andExpect(jsonPath("$.items[1].text").isEmpty());
        mvc.perform(get(REVIEWS).param("status", "APPROVED").param("page", "0").with(ADMIN))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].authorName").value("Carla"));
    }

    @Test
    void givenARejectedReview_whenTheProductPageIsRead_thenItDoesNotAppearOrCountTowardTheAverage() throws Exception {
        stored("Ana", 4, null, "APPROVED", T0);
        UUID bruno = stored("Bruno", 1, "Feo", "APPROVED", T0.plusSeconds(60));

        mvc.perform(post(REVIEWS + "/" + bruno + "/reject").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(bruno.toString()))
            .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(get(LUNA_PAGE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.average").value(4.0))
            .andExpect(jsonPath("$.reviews[*].authorName", contains("Ana")));
    }

    // --- Business rules ---

    @Test
    void givenAPendingReview_whenApproved_thenItShowsOnTheProductPageAndRepeatingTheDecisionChangesNothing()
        throws Exception {
        UUID review = stored("Ana", 5, "Muy cómodo", "PENDING", T0);

        mvc.perform(post(REVIEWS + "/" + review + "/approve").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.product.name").value("Pijama Luna"));
        OffsetDateTime decidedAt = moderatedAt(review);
        assertThat(decidedAt).isNotNull();

        mvc.perform(post(REVIEWS + "/" + review + "/approve").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(moderatedAt(review)).isEqualTo(decidedAt);
        mvc.perform(get(LUNA_PAGE))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.reviews[0].text").value("Muy cómodo"));
        mvc.perform(get(REVIEWS).with(ADMIN)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void givenAReview_whenDeleted_thenItIsGoneAndDeletingItAgainIsNotFound() throws Exception {
        UUID review = stored("Ana", 5, null, "APPROVED", T0);

        mvc.perform(delete(REVIEWS + "/" + review).with(ADMIN).with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(delete(REVIEWS + "/" + review).with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(owner.queryForObject("select count(*) from review", Integer.class)).isZero();
        mvc.perform(get(LUNA_PAGE)).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void givenAnUnknownReview_whenApprovedOrRejected_thenItIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(post(REVIEWS + "/" + unknown + "/approve").with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(REVIEWS + "/" + unknown + "/reject").with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void givenAnUnknownStatusOrAPageOutOfRange_whenListing_thenTheRequestIsRefused() throws Exception {
        mvc.perform(get(REVIEWS).param("status", "DELETED").with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(REVIEWS).param("page", "-1").with(ADMIN))
            .andExpect(status().isBadRequest());
    }

    @Test
    void givenSomeoneOtherThanTheAdministratorOrNoCsrfToken_whenModerating_thenItIsRefusedAndNothingChanges()
        throws Exception {
        UUID review = stored("Ana", 5, null, "PENDING", T0);
        RequestPostProcessor customer = user(UUID.randomUUID().toString()).roles("CUSTOMER");

        mvc.perform(get(REVIEWS).with(customer)).andExpect(status().isForbidden());
        mvc.perform(post(REVIEWS + "/" + review + "/approve").with(customer).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post(REVIEWS + "/" + review + "/approve").with(ADMIN))
            .andExpect(status().isForbidden());
        mvc.perform(delete(REVIEWS + "/" + review).with(ADMIN))
            .andExpect(status().isForbidden());
        mvc.perform(get(REVIEWS)).andExpect(status().isUnauthorized());

        assertThat(owner.queryForObject("select status from review where id = ?", String.class, review))
            .isEqualTo("PENDING");
    }
}
