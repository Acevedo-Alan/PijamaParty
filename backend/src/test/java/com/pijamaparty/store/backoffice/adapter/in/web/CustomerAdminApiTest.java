package com.pijamaparty.store.backoffice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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

/** UC-34 (T-E8-10): "Clientes" through the back office API, with the real security chain and PostgreSQL (V44). */
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
