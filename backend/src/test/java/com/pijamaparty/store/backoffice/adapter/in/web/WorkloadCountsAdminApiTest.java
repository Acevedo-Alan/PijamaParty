package com.pijamaparty.store.backoffice.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E8-05 through the back-office API, with the real security filter chain, Row Level Security and PostgreSQL: the
 * menu's count of paid orders. Orders stored as the owner.
 */
@ApiTest
class WorkloadCountsAdminApiTest {

    private static final String COUNTS = "/api/admin/dashboard/counts";
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-31T15:30:00Z");

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    WorkloadCountsAdminApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    // Orders other API tests left behind would count too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenTwoPaidOrders_whenTheBackOfficeOpens_thenPedidosShowsTwo() throws Exception {
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PAID, EXPIRES_AT);
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PAID, EXPIRES_AT);
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PREPARING, EXPIRES_AT);

        mvc.perform(get(COUNTS).with(user(UUID.randomUUID().toString()).roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.paidOrders").value(2));
    }

    @Test
    void givenACustomerOrNobody_whenAskingForTheCounts_thenItIsForbiddenOrUnauthorized() throws Exception {
        mvc.perform(get(COUNTS).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get(COUNTS)).andExpect(status().isUnauthorized());
    }
}
