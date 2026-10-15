package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-29 (T-E3-26) through the back-office API, with the real security filter chain, Row Level Security and
 * PostgreSQL: a guest sends the withdrawal form, and the administrator sees the request in the list and on its order,
 * and resolves it. Orders stored as the owner, for {@code ana@example.com}.
 */
@ApiTest
class WithdrawalAdminApiTest {

    private static final String WITHDRAWALS = "/api/admin/withdrawals";
    private static final String EMAIL = "ana@example.com";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;

    WithdrawalAdminApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox, withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAPaidOrder_whenTheFormIsSent_thenTheRequestAppearsInTheBackOfficeWithItsOrder() throws Exception {
        OrderId order = paidOrder();
        long number = OrderRows.numberOf(owner, order);
        String code = request(number);

        mvc.perform(get(WITHDRAWALS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].code", contains(code)))
            .andExpect(jsonPath("$.items[0].orderId").value(order.value().toString()))
            .andExpect(jsonPath("$.items[0].orderNumber").value(number))
            .andExpect(jsonPath("$.items[0].orderStatus").value("PAID"))
            .andExpect(jsonPath("$.items[0].buyerName").value("Ana Pérez"))
            .andExpect(jsonPath("$.items[0].outOfTerm").value(false))
            .andExpect(jsonPath("$.items[0].status").value("RECEIVED"))
            .andExpect(jsonPath("$.items[0].resolvedAt").isEmpty())
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/admin/orders/{id}", order.value()).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alerts", contains("WITHDRAWAL_REQUESTED")))
            .andExpect(jsonPath("$.withdrawal.code").value(code))
            .andExpect(jsonPath("$.withdrawal.outOfTerm").value(false));
    }

    @Test
    void givenAnOrderDeliveredMoreThanTenDaysAgo_whenTheFormIsSent_thenTheBackOfficeShowsItOutOfTerm()
        throws Exception {
        OrderId order = paidOrder();
        Instant deliveredAt = Instant.now().minus(15, ChronoUnit.DAYS);
        owner.update("update orders set status = 'DELIVERED', shipment_tracking_number = 'CA123456789AR', "
            + "delivered_at = ?, created_at = ? where id = ?", deliveredAt.atOffset(ZoneOffset.UTC),
            deliveredAt.minus(3, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC), order.value());
        String code = request(OrderRows.numberOf(owner, order));

        mvc.perform(get(WITHDRAWALS).param("status", "RECEIVED").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].code", contains(code)))
            .andExpect(jsonPath("$.items[0].orderStatus").value("DELIVERED"))
            .andExpect(jsonPath("$.items[0].outOfTerm").value(true));
        mvc.perform(get("/api/admin/orders/{id}", order.value()).with(ADMIN))
            .andExpect(jsonPath("$.withdrawal.outOfTerm").value(true));
    }

    @Test
    void givenAReceivedRequest_whenResolved_thenItLeavesTheActiveOnesAndTheOrderIsNotTouched() throws Exception {
        OrderId order = paidOrder();
        String code = request(OrderRows.numberOf(owner, order));
        UUID id = idOf(code);

        resolve(id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        mvc.perform(get(WITHDRAWALS).param("status", "RECEIVED").with(ADMIN))
            .andExpect(jsonPath("$.items", empty()));
        mvc.perform(get(WITHDRAWALS).param("status", "RESOLVED").with(ADMIN))
            .andExpect(jsonPath("$.items[*].code", contains(code)))
            .andExpect(jsonPath("$.items[0].resolvedAt").isNotEmpty());
        mvc.perform(get("/api/admin/orders/{id}", order.value()).with(ADMIN))
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.alerts", empty()))
            .andExpect(jsonPath("$.withdrawal").isEmpty());
    }

    @Test
    void givenAResolvedRequest_whenResolvedAgain_thenItIsAConflictAndKeepsItsResolutionDate() throws Exception {
        UUID id = idOf(request(OrderRows.numberOf(owner, paidOrder())));
        String resolvedAt = json.readTree(resolve(id).andReturn().getResponse().getContentAsString())
            .get("resolvedAt").asText();

        resolve(id)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("WITHDRAWAL_ALREADY_RESOLVED"))
            .andExpect(jsonPath("$.resolvedAt").value(resolvedAt));
    }

    @Test
    void givenAResolvedRequest_whenTheFormIsSentAgain_thenANewCodeIsIssued() throws Exception {
        long number = OrderRows.numberOf(owner, paidOrder());
        String first = request(number);
        resolve(idOf(first)).andExpect(status().isOk());

        String second = request(number);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(WITHDRAWALS).with(ADMIN))
            .andExpect(jsonPath("$.items[*].code", contains(second, first)));
    }

    @Test
    void givenNoSuchRequest_whenResolved_thenItIsNotFound() throws Exception {
        resolve(UUID.randomUUID())
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("WITHDRAWAL_NOT_FOUND"));
    }

    @Test
    void givenRequestsOutOfShape_whenTheyArrive_thenTheyAreBadRequests() throws Exception {
        mvc.perform(get(WITHDRAWALS).param("status", "LOST").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(WITHDRAWALS).param("page", "-1").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(WITHDRAWALS).param("page", "10001").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(post(WITHDRAWALS + "/not-a-uuid/resolve").with(ADMIN).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenManagingWithdrawals_thenAccessIsDenied() throws Exception {
        UUID id = idOf(request(OrderRows.numberOf(owner, paidOrder())));

        mvc.perform(get(WITHDRAWALS)).andExpect(status().isUnauthorized());
        mvc.perform(get(WITHDRAWALS).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(post(WITHDRAWALS + "/{id}/resolve", id).with(user(UUID.randomUUID().toString())
                .roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post(WITHDRAWALS + "/{id}/resolve", id).with(ADMIN)).andExpect(status().isForbidden());
        assertThat(owner.queryForObject("select status from withdrawal_request where id = ?", String.class, id))
            .isEqualTo("RECEIVED");
    }

    private OrderId paidOrder() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now().minusSeconds(3600));
        return order;
    }

    /** A guest sends the withdrawal form for the order; answers the code. */
    private String request(long number) throws Exception {
        String body = mvc.perform(post("/api/withdrawals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("orderNumber", Long.toString(number), "email", EMAIL))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("code").asText();
    }

    private UUID idOf(String code) {
        return owner.queryForObject("select id from withdrawal_request where code = ?", UUID.class, code);
    }

    private ResultActions resolve(UUID id) throws Exception {
        return mvc.perform(post(WITHDRAWALS + "/{id}/resolve", id).with(ADMIN).with(csrf()));
    }
}
