package com.pijamaparty.store.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.application.AdjustStock;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/** UC-07 through the back office API, with the real security filter chain and PostgreSQL. */
@ApiTest
class StockAdminApiTest {

    private static final String ADJUSTMENTS = "/api/admin/stock-adjustments";
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final RequestPostProcessor ADMIN = user(ADMIN_ID.toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final AdjustStock adjustStock;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;
    private UUID variant;

    StockAdminApiTest(MockMvc mvc, ObjectMapper json, AdjustStock adjustStock, JdbcTemplate jdbc,
                      PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.adjustStock = adjustStock;
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A variant whose stock level was opened when it was created (UC-06), and the administrator's user. */
    @BeforeEach
    void openVariant() {
        variant = UUID.randomUUID();
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 0, now())", variant);
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'owner@example.com', true, '{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    'Dueña', 'ADMIN', 0, now(), now())
            """, ADMIN_ID.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, admin_recovery_code, app_user");
    }

    @Test
    void givenAValidAdjustment_whenSaved_thenTheHistoryShowsDateUserAmountAndReason() throws Exception {
        adjust(UUID.randomUUID().toString(), variant, 4, "Reposición")
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.variantId").value(variant.toString()))
            .andExpect(jsonPath("$.onHand").value(4));

        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variantId").value(variant.toString()))
            .andExpect(jsonPath("$.onHand").value(4))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id", notNullValue()))
            .andExpect(jsonPath("$.items[0].type").value("INITIAL_LOAD"))
            .andExpect(jsonPath("$.items[0].quantity").value(4))
            .andExpect(jsonPath("$.items[0].note").value("Reposición"))
            .andExpect(jsonPath("$.items[0].userId").value(ADMIN_ID.toString()))
            .andExpect(jsonPath("$.items[0].userName").value("Dueña"))
            .andExpect(jsonPath("$.items[0].createdAt", notNullValue()));
    }

    @Test
    void givenAStockOf3_whenSubtracting5_thenItIsRejected() throws Exception {
        adjust(UUID.randomUUID().toString(), variant, 3, null).andExpect(status().isCreated());

        adjust(UUID.randomUUID().toString(), variant, -5, null)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("WOULD_GO_NEGATIVE"))
            .andExpect(jsonPath("$.onHand").value(3));

        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements").with(ADMIN))
            .andExpect(jsonPath("$.onHand").value(3))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void givenAStockOf3With2ReservedByOrders_whenSubtracting2_thenItIsRejectedWithTheReservedUnits()
        throws Exception {
        adjust(UUID.randomUUID().toString(), variant, 3, null).andExpect(status().isCreated());
        app.update("update stock_level set reserved = 2 where variant_id = ?", variant);

        adjust(UUID.randomUUID().toString(), variant, -2, null)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("WOULD_GO_BELOW_RESERVED"))
            .andExpect(jsonPath("$.onHand").value(3))
            .andExpect(jsonPath("$.reserved").value(2));

        adjust(UUID.randomUUID().toString(), variant, -1, null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.onHand").value(2));
    }

    @Test
    void givenAnAdjustmentWhoseResponseWasLost_whenRetriedWithTheSameKey_thenTheStockChangesOnlyOnce()
        throws Exception {
        String key = UUID.randomUUID().toString();
        adjust(key, variant, 2, "Reposición").andExpect(status().isCreated());

        adjust(key, variant, 2, "Reposición")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onHand").value(2));
        adjust(key, variant, 3, "Reposición")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("KEY_REUSED"));

        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements").with(ADMIN))
            .andExpect(jsonPath("$.onHand").value(2))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void givenSameKeyAdjustmentsAtOnce_whenTheyRun_thenOneIsRecordedAndTheOthersAnswerIt() throws Exception {
        var command = new AdjustStock.Command(Actor.admin(ADMIN_ID), VariantId.of(variant),
            AdjustmentKey.of(UUID.randomUUID()), 5, Optional.empty());

        List<AdjustStock.Result> outcomes = runAtOnce(List.of(command, command, command, command));

        assertThat(outcomes).filteredOn(AdjustStock.Result.Adjusted.class::isInstance).hasSize(1);
        assertThat(outcomes).containsOnly(new AdjustStock.Result.Adjusted(5), new AdjustStock.Result.AlreadyAdjusted(5));
        assertThat(onHand()).isEqualTo(5);
        assertThat(movements()).isOne();
    }

    @Test
    void givenTheLastUnit_whenTwoSubtractionsRunAtOnce_thenOnlyOneSucceedsAndStockNeverGoesNegative()
        throws Exception {
        adjust(UUID.randomUUID().toString(), variant, 1, null).andExpect(status().isCreated());
        Actor admin = Actor.admin(ADMIN_ID);

        List<AdjustStock.Result> outcomes = runAtOnce(List.of(
            new AdjustStock.Command(admin, VariantId.of(variant), AdjustmentKey.of(UUID.randomUUID()), -1,
                Optional.empty()),
            new AdjustStock.Command(admin, VariantId.of(variant), AdjustmentKey.of(UUID.randomUUID()), -1,
                Optional.empty())));

        assertThat(outcomes).containsExactlyInAnyOrder(new AdjustStock.Result.Adjusted(0),
            new AdjustStock.Result.WouldGoNegative(0));
        assertThat(onHand()).isZero();
        assertThat(movements()).isEqualTo(2);
    }

    @Test
    void givenInvalidAdjustments_whenSent_thenTheyAreRejectedAsProblemJson() throws Exception {
        adjust("not-a-uuid", variant, 1, null)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(ADJUSTMENTS).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(variant, 1, null)))
            .andExpect(status().isBadRequest());
        mvc.perform(post(ADJUSTMENTS).with(ADMIN).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":\"" + variant + "\"}"))
            .andExpect(status().isBadRequest());
        adjust(UUID.randomUUID().toString(), variant, 0, null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("INVALID_QUANTITY"));
        adjust(UUID.randomUUID().toString(), variant, 10_001, null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("INVALID_QUANTITY"));
        adjust(UUID.randomUUID().toString(), variant, 1, "x".repeat(201))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("INVALID_NOTE"));

        assertThat(movements()).isZero();
    }

    @Test
    void givenAnUnknownVariant_whenAdjustingOrListing_thenItIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        adjust(UUID.randomUUID().toString(), unknown, 1, null)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Variant not found"));
        mvc.perform(get("/api/admin/variants/" + unknown + "/stock-movements").with(ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenAMovementWithoutNoteByAUserTheAdministratorCannotSee_whenListing_thenNoteAndNameAreNull()
        throws Exception {
        var other = Actor.admin(UserId.of(UUID.randomUUID()));
        CurrentActor.callAs(other, () -> adjustStock.execute(new AdjustStock.Command(other, VariantId.of(variant),
            AdjustmentKey.of(UUID.randomUUID()), 1, Optional.empty())));

        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements").with(ADMIN))
            .andExpect(jsonPath("$.items[0].note").value(nullValue()))
            .andExpect(jsonPath("$.items[0].userName").value(nullValue()));
    }

    @Test
    void givenSomeoneOtherThanTheAdministratorOrNoCsrfToken_whenAdjusting_thenItIsRefused() throws Exception {
        RequestPostProcessor customer = user(UUID.randomUUID().toString()).roles("CUSTOMER");

        mvc.perform(post(ADJUSTMENTS).with(ADMIN).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body(variant, 1, null)))
            .andExpect(status().isForbidden());
        mvc.perform(post(ADJUSTMENTS).with(customer).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body(variant, 1, null)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements").with(customer))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements"))
            .andExpect(status().isUnauthorized());

        assertThat(onHand()).isZero();
    }

    private List<AdjustStock.Result> runAtOnce(List<AdjustStock.Command> commands) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(commands.size());
        List<AdjustStock.Result> outcomes = new ArrayList<>();
        try {
            List<Future<AdjustStock.Result>> results = new ArrayList<>();
            for (AdjustStock.Command command : commands) {
                results.add(pool.submit(() -> {
                    start.await();
                    return CurrentActor.callAs(command.actor(), () -> adjustStock.execute(command));
                }));
            }
            start.countDown();
            for (Future<AdjustStock.Result> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return outcomes;
    }

    private ResultActions adjust(String key, UUID variantId, int quantity, String note) throws Exception {
        return mvc.perform(post(ADJUSTMENTS).with(ADMIN).with(csrf()).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(body(variantId, quantity, note)));
    }

    private String body(UUID variantId, int quantity, String note) throws Exception {
        return json.writeValueAsString(new StockAdminController.AdjustmentRequest(variantId, quantity, note));
    }

    private Integer onHand() {
        return owner.queryForObject("select on_hand from stock_level where variant_id = ?", Integer.class, variant);
    }

    private Integer movements() {
        return owner.queryForObject("select count(*) from stock_movement where variant_id = ?", Integer.class,
            variant);
    }
}
