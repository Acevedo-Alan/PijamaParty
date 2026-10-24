package com.pijamaparty.store.activity.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-37 (T-E8-01) through the back office API, with the real security filter chain, Row Level Security and
 * PostgreSQL. The entries are stored as the owner.
 */
@ApiTest
class ActivityAdminApiTest {

    private static final String ACTIVITY = "/api/admin/activity";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final RequestPostProcessor ADMIN = user(ADMIN_ID.toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    ActivityAdminApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    private UUID stored(String occurredAt, String area, String action, String subjectType, UUID subjectId,
                        String label, String changes) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into activity_entry (id, occurred_at, actor_id, area, action, subject_type, subject_id,
                                        subject_label, changes)
            values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """, id, Timestamp.from(Instant.parse(occurredAt)), ADMIN_ID, area, action, subjectType, subjectId, label,
            changes);
        return id;
    }

    // --- Acceptance criteria of UC-37 ---

    @Test
    void givenAPriceChange_whenTheAdministratorOpensActividad_thenItShowsTheSkuThePricesAndTheTime() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID id = stored("2026-10-15T15:30:00Z", "CATALOG", "VARIANT_UPDATED", "VARIANT", variant, "PP-AB12CD34",
            "[{\"field\": \"price\", \"before\": \"$20.000\", \"after\": \"$22.000\"}]");

        mvc.perform(get(ACTIVITY).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(id.toString()))
            .andExpect(jsonPath("$.items[0].occurredAt").value("2026-10-15T15:30:00Z"))
            .andExpect(jsonPath("$.items[0].area").value("CATALOG"))
            .andExpect(jsonPath("$.items[0].action").value("VARIANT_UPDATED"))
            .andExpect(jsonPath("$.items[0].subject.type").value("VARIANT"))
            .andExpect(jsonPath("$.items[0].subject.id").value(variant.toString()))
            .andExpect(jsonPath("$.items[0].subject.label").value("PP-AB12CD34"))
            .andExpect(jsonPath("$.items[0].changes[0].field").value("price"))
            .andExpect(jsonPath("$.items[0].changes[0].before").value("$20.000"))
            .andExpect(jsonPath("$.items[0].changes[0].after").value("$22.000"));
    }

    @Test
    void givenTheStockFilterAndAPeriod_whenApplied_thenOnlyStockEntriesOfThoseArgentinaDaysShow() throws Exception {
        stored("2026-10-01T02:59:00Z", "STOCK", "STOCK_ADJUSTED", "VARIANT", UUID.randomUUID(), "PP-1", "[]");
        UUID first = stored("2026-10-01T03:00:00Z", "STOCK", "STOCK_ADJUSTED", "VARIANT", UUID.randomUUID(), "PP-2",
            "[]");
        UUID last = stored("2026-10-16T02:59:00Z", "STOCK", "STOCK_ADJUSTED", "VARIANT", UUID.randomUUID(), "PP-3",
            "[]");
        stored("2026-10-16T03:00:00Z", "STOCK", "STOCK_ADJUSTED", "VARIANT", UUID.randomUUID(), "PP-4", "[]");
        stored("2026-10-10T15:00:00Z", "PROMOTIONS", "COUPON_CREATED", "COUPON", UUID.randomUUID(), "VERANO", "[]");

        mvc.perform(get(ACTIVITY).param("area", "STOCK").param("from", "2026-10-01").param("to", "2026-10-15")
                .with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", contains(last.toString(), first.toString())));
    }

    @Test
    void givenAnEntryWithoutSubjectIdLabelOrChanges_whenListed_thenThoseAreNullOrEmpty() throws Exception {
        stored("2026-10-15T15:30:00Z", "SHIPPING", "SHIPPING_ZONES_CHANGED", "SHIPPING", null, null, "[]");

        mvc.perform(get(ACTIVITY).with(ADMIN))
            .andExpect(jsonPath("$.items[0].subject.id").isEmpty())
            .andExpect(jsonPath("$.items[0].subject.label").isEmpty())
            .andExpect(jsonPath("$.items[0].changes", hasSize(0)));
    }

    @Test
    void given51Entries_whenTheFirstPageIsListed_then50ShowWithMore() throws Exception {
        for (int i = 0; i < 51; i++) {
            stored(Instant.parse("2026-10-15T15:00:00Z").plusSeconds(i).toString(), "REVIEWS", "REVIEW_APPROVED",
                "REVIEW", UUID.randomUUID(), "Pijama Luna", "[]");
        }

        mvc.perform(get(ACTIVITY).with(ADMIN))
            .andExpect(jsonPath("$.items", hasSize(50)))
            .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get(ACTIVITY).param("page", "1").with(ADMIN))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.hasMore").value(false));
    }

    // --- Errors and access ---

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"area, PAYMENTS", "from, 2026-13-01", "to, 15/10/2026", "page, 10001", "page, -1"})
    void givenAnInvalidParameter_whenListing_thenItIsABadRequest(String name, String value) throws Exception {
        mvc.perform(get(ACTIVITY).param(name, value).with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenFromAfterTo_whenListing_thenItIsABadRequest() throws Exception {
        mvc.perform(get(ACTIVITY).param("from", "2026-10-15").param("to", "2026-10-01").with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid period"));
    }

    @Test
    void givenACustomerOrNobody_whenListing_thenItIsForbiddenOrUnauthorized() throws Exception {
        stored("2026-10-15T15:30:00Z", "STOCK", "STOCK_ADJUSTED", "VARIANT", UUID.randomUUID(), "PP-1", "[]");

        mvc.perform(get(ACTIVITY).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get(ACTIVITY)).andExpect(status().isUnauthorized());
    }
}
