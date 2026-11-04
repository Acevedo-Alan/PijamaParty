package com.pijamaparty.store.backoffice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-34 (T-E8-10, T-E8-11): "Clientes" and a customer's profile through the back office API, with the real security
 * chain and PostgreSQL (V44).
 */
@ApiTest
class CustomerAdminApiTest {

    private static final String CUSTOMERS = "/api/admin/customers";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");
    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String INSERT_USER = """
        insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        values (?, ?, true, ?, ?, ?, 0, ?, ?)
        """;
    private static final String INSERT_ORDER = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at)
        values (?, nextval('order_number_seq'), ?, md5(random()::text) || md5(random()::text), 'PAID', 'Ana', 'Gómez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                2000000, 0, 300000, 2300000, 'MERCADOPAGO', '2026-10', ?, ?, ?)
        """;

    private static final String INSERT_ORDER_IN = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at,
                            shipment_tracking_number, delivered_at)
        values (?, nextval('order_number_seq'), ?, md5(random()::text) || md5(random()::text), ?, 'Ana', 'Gómez',
                'ana_1@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                ?, 0, 0, ?, 'MERCADOPAGO', '2026-10', ?, ?, ?, ?, ?)
        """;

    private final MockMvc mvc;
    private final JdbcTemplate owner;
    private UUID ana;
    private UUID juan;
    private UUID lia;

    CustomerAdminApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void seed() {
        cleanUp();
        ana = storedUser("Ana Gómez", "ana_1@example.com", "CUSTOMER", "2026-10-05T15:00:00Z");
        juan = storedUser("Juan Pérez", "ana11@example.com", "CUSTOMER", "2026-10-15T15:00:00Z");
        lia = storedUser("Lía Sosa", "lia@example.com", "CUSTOMER", "2026-10-20T15:00:00Z");
        storedUser("Dueña", "owner@example.com", "ADMIN", "2026-10-25T15:00:00Z");
        order(ana);
        order(ana);
        order(null);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
        owner.execute("truncate address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
        owner.execute("truncate activity_entry");
    }

    @Test
    void givenACustomerWithADeliveredACancelledAndAnAwaitingOrder_whenItsProfileOpens_thenThreeOrdersOnePurchase()
        throws Exception {
        UUID owned = storedUser("Rosa Paz", "rosa@example.com", "CUSTOMER", "2026-10-01T15:00:00Z");
        owner.update("update app_user set phone = '3425559876' where id = ?", owned);
        owner.update("""
            insert into address (id, user_id, street, street_number, floor, apartment, city, province, postal_code,
                                 is_primary, created_at, updated_at)
            values (?, ?, 'Rivadavia', '100', null, null, 'Rosario', 'SANTA_FE', 'S2000ABC', false, now(), now()),
                   (?, ?, 'San Martín', '2550', '3', 'B', 'Santa Fe', 'SANTA_FE', 'S3000ABC', true, now(), now())
            """, UUID.randomUUID(), owned, UUID.randomUUID(), owned);
        owner.update("""
            insert into external_identity (id, user_id, provider, subject, email, created_at)
            values (?, ?, 'GOOGLE', 'google-subject-1', 'rosa.google@example.com', now())
            """, UUID.randomUUID(), owned);
        orderIn(owned, "DELIVERED", 3_000_000, "2026-10-10T15:00:00Z");
        orderIn(owned, "CANCELLED", 1_000_000, "2026-10-11T15:00:00Z");
        orderIn(owned, "AWAITING_PAYMENT", 500_000, "2026-10-12T15:00:00Z");

        mvc.perform(get(CUSTOMERS + "/" + owned).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Rosa Paz"))
            .andExpect(jsonPath("$.email").value("rosa@example.com"))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.phone").value("3425559876"))
            .andExpect(jsonPath("$.registeredAt").value("2026-10-01T15:00:00Z"))
            .andExpect(jsonPath("$.providers", contains("GOOGLE")))
            .andExpect(jsonPath("$.addresses[*].street", contains("San Martín", "Rivadavia")))
            .andExpect(jsonPath("$.addresses[0].primary").value(true))
            .andExpect(jsonPath("$.addresses[0].floor").value("3"))
            .andExpect(jsonPath("$.orders[*].status", contains("AWAITING_PAYMENT", "CANCELLED", "DELIVERED")))
            .andExpect(jsonPath("$.orders[2].totalCents").value(3_000_000))
            .andExpect(jsonPath("$.hasMoreOrders").value(false))
            .andExpect(jsonPath("$.orderCount").value(3))
            .andExpect(jsonPath("$.purchases").value(1))
            .andExpect(jsonPath("$.spentCents").value(3_000_000))
            .andExpect(jsonPath("$.lastPurchaseAt").value("2026-10-10T15:00:00Z"))
            .andExpect(content().string(not(containsString("google-subject-1"))))
            .andExpect(content().string(not(containsString("rosa.google@"))));
    }

    @Test
    void givenAProfileOpened_whenActividadOpens_thenOneCustomerViewedWithoutNameOrEmailAndNoneForTheSecondPage()
        throws Exception {
        mvc.perform(get(CUSTOMERS + "/" + ana).with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get(CUSTOMERS + "/" + ana).param("ordersPage", "1").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").isEmpty());

        var entries = owner.queryForList("select action, area, subject_type, subject_id, subject_label, changes::text "
            + "as changes from activity_entry");
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst()).containsEntry("action", "CUSTOMER_VIEWED").containsEntry("area", "CUSTOMERS")
            .containsEntry("subject_type", "CUSTOMER").containsEntry("subject_id", ana);
        assertThat(entries.getFirst().toString()).doesNotContain("Ana", "ana_1");
    }

    @Test
    void givenAnUnknownIdOrTheAdministrator_whenTheProfileIsAskedFor_thenItIsNotFoundAndNothingIsRecorded()
        throws Exception {
        UUID admin = owner.queryForObject("select id from app_user where role = 'ADMIN'", UUID.class);

        mvc.perform(get(CUSTOMERS + "/" + UUID.randomUUID()).with(ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("CUSTOMER_NOT_FOUND"));
        mvc.perform(get(CUSTOMERS + "/" + admin).with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get(CUSTOMERS + "/" + ana).param("ordersPage", "-1").with(ADMIN))
            .andExpect(status().isBadRequest());
        assertThat(owner.queryForObject("select count(*) from activity_entry", Long.class)).isZero();
    }

    @Test
    void givenACustomerSessionOrNone_whenAProfileIsCalled_thenItIsRefused() throws Exception {
        mvc.perform(get(CUSTOMERS + "/" + ana)).andExpect(status().isUnauthorized());
        mvc.perform(get(CUSTOMERS + "/" + ana).with(user(ana.toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    private void orderIn(UUID customer, String status, long totalCents, String placedAt) {
        var at = Instant.parse(placedAt).atOffset(ZoneOffset.UTC);
        boolean shipped = status.equals("SHIPPED") || status.equals("DELIVERED");
        owner.update(INSERT_ORDER_IN, UUID.randomUUID(), customer, status, totalCents, totalCents,
            at.plusMinutes(30), at, at, shipped ? "CA123456789AR" : null, status.equals("DELIVERED") ? at : null);
    }

    @Test
    void givenThreeCustomersAndTheAdministrator_whenClientesOpens_thenTheCustomersShowNewestFirstWithTheirOrders()
        throws Exception {
        mvc.perform(get(CUSTOMERS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", contains(lia.toString(), juan.toString(), ana.toString())))
            .andExpect(jsonPath("$.items[2].name").value("Ana Gómez"))
            .andExpect(jsonPath("$.items[2].email").value("ana_1@example.com"))
            .andExpect(jsonPath("$.items[2].registeredAt").value("2026-10-05T15:00:00Z"))
            .andExpect(jsonPath("$.items[2].orders").value(2))
            .andExpect(jsonPath("$.items[0].orders").value(0))
            .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void givenSearchesAndARegistrationFilter_whenApplied_thenTheMatchesShowAndTheSearchIsNeverLogged(
        CapturedOutput logs) throws Exception {
        mvc.perform(get(CUSTOMERS).param("q", "GÓMEZ").with(ADMIN))
            .andExpect(jsonPath("$.items[*].id", contains(ana.toString())));
        mvc.perform(get(CUSTOMERS).param("q", "gomez").with(ADMIN))
            .andExpect(jsonPath("$.items[*].id", contains(ana.toString())));
        mvc.perform(get(CUSTOMERS).param("q", "ana_1").with(ADMIN))
            .andExpect(jsonPath("$.items[*].id", contains(ana.toString())));
        mvc.perform(get(CUSTOMERS).param("from", "2026-10-01").param("to", "2026-10-15").with(ADMIN))
            .andExpect(jsonPath("$.items[*].id", contains(juan.toString(), ana.toString())));

        assertThat(logs.getAll()).doesNotContain("GÓMEZ", "gomez", "ana_1");
    }

    @Test
    void givenInvalidFilters_whenSent_thenTheyAreRejectedAsProblemJson() throws Exception {
        mvc.perform(get(CUSTOMERS).param("q", "a").with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INVALID_SEARCH"));
        mvc.perform(get(CUSTOMERS).param("from", "2026-10-16").param("to", "2026-10-15").with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.problem").value("INVALID_PERIOD"));
        mvc.perform(get(CUSTOMERS).param("from", "15/10/2026").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(CUSTOMERS).param("to", "+999999999-12-31").with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.problem").value("INVALID_PERIOD"));
        mvc.perform(get(CUSTOMERS).param("page", "-1").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void givenACustomerSessionOrNone_whenTheListIsCalled_thenItIsRefused() throws Exception {
        mvc.perform(get(CUSTOMERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(CUSTOMERS).with(user(ana.toString()).roles("CUSTOMER"))).andExpect(status().isForbidden());
    }

    private UUID storedUser(String name, String email, String role, String registeredAt) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(Instant.parse(registeredAt));
        owner.update(INSERT_USER, id, email, PASSWORD_HASH, name, role, at, at);
        return id;
    }

    private void order(UUID customer) {
        var at = Instant.parse("2026-10-20T15:00:00Z").atOffset(ZoneOffset.UTC);
        owner.update(INSERT_ORDER, UUID.randomUUID(), customer, at.plusMinutes(30), at, at);
    }
}
