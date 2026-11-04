package com.pijamaparty.store.backoffice.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-33 through the back-office API, with the real security filter chain, Row Level Security and PostgreSQL. Orders
 * stored as the owner; the amounts read today's real date, so the test checks the shape and the counts, not the days.
 */
@ApiTest
class DashboardAdminApiTest {

    private static final String DASHBOARD = "/api/admin/dashboard";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    DashboardAdminApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
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
    void givenTwoPaidOrdersNow_whenTheSummaryOpens_thenTheyCountTodayAndWaitToBePrepared() throws Exception {
        // The reservation ends 30 minutes after the order is created: created now.
        Instant soon = Instant.now().plusSeconds(30 * 60);
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PAID, soon);
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PAID, soon);

        mvc.perform(get(DASHBOARD).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todo.paidOrders").value(2))
            .andExpect(jsonPath("$.todo.preparingOrders").value(0))
            .andExpect(jsonPath("$.sales.period").value("MONTH"))
            .andExpect(jsonPath("$.sales.orders").value(2))
            .andExpect(jsonPath("$.sales.amountCents").value(4_600_000))
            .andExpect(jsonPath("$.sales.averageTicketCents").value(2_300_000))
            .andExpect(jsonPath("$.chart.kind").value("DAYS"))
            .andExpect(jsonPath("$.chart.points", hasSize(30)))
            .andExpect(jsonPath("$.lowStock.threshold").value(3))
            .andExpect(jsonPath("$.recentOrders", hasSize(2)))
            .andExpect(jsonPath("$.recentOrders[0].buyerName").value("Ana Pérez"))
            .andExpect(jsonPath("$.recentOrders[0].status").value("PAID"));
        mvc.perform(get(DASHBOARD).param("period", "TODAY").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sales.period").value("TODAY"))
            .andExpect(jsonPath("$.sales.orders").value(2))
            .andExpect(jsonPath("$.sales.variation").isEmpty());
    }

    @Test
    void givenACustomPeriod_whenTheSummaryOpens_thenTheSalesCarryItsDates() throws Exception {
        String today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).toString();

        mvc.perform(get(DASHBOARD).param("period", "CUSTOM").param("from", today).param("to", today).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sales.period").value("CUSTOM"))
            .andExpect(jsonPath("$.sales.from").value(today))
            .andExpect(jsonPath("$.sales.to").value(today));
    }

    @Test
    void givenACustomPeriodThatIsNotOne_whenTheSummaryIsAsked_thenItIsAProblem() throws Exception {
        mvc.perform(get(DASHBOARD).param("period", "CUSTOM").param("from", "2026-10-05").param("to", "2026-10-04")
                .with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.title").value("Invalid period"));
        mvc.perform(get(DASHBOARD).param("period", "CUSTOM").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(DASHBOARD).param("period", "YEAR").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void givenAThresholdOf0InTheStoreSettings_whenTheSummaryOpens_thenLowStockUsesIt() throws Exception {
        owner.update("update store_settings set low_stock_threshold = 0");
        try {
            // UC-36: with 0, only the sold-out variants are low (catalog.api.LowStockVariants).
            mvc.perform(get(DASHBOARD).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStock.threshold").value(0));
        } finally {
            owner.update("update store_settings set low_stock_threshold = 3");
        }
    }

    @Test
    void givenTheChartByMonths_whenTheSummaryOpens_thenItHas12Months() throws Exception {
        mvc.perform(get(DASHBOARD).param("chart", "MONTHS").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chart.kind").value("MONTHS"))
            .andExpect(jsonPath("$.chart.points", hasSize(12)));
    }

    @Test
    void givenAnotherChart_whenTheSummaryIsAsked_thenItIsABadRequest() throws Exception {
        mvc.perform(get(DASHBOARD).param("chart", "WEEKS").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void givenACustomerOrNobody_whenTheSummaryIsAsked_thenItIsForbiddenOrUnauthorized() throws Exception {
        mvc.perform(get(DASHBOARD).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized());
    }
}
