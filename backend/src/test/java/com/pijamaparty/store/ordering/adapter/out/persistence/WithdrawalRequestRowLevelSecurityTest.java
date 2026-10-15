package com.pijamaparty.store.ordering.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-27 (UC-29, RNF-27): the Row Level Security matrix of {@code withdrawal_request} (V25, V26) and of the
 * withdrawal emails in {@code email_outbox} (V26), with customer A (owner of the first order), customer B, a guest,
 * a guest without a token, the administrator, the system and a missing context. A guest or a customer reaches only
 * the requests of the order it matched by number and email in that transaction ({@code lock_withdrawable_order}):
 * it reads them, records one still to resolve and enqueues its two emails; owning the order is not enough. The
 * administrator reads and resolves them, the system reads them, and nobody deletes them. Every transaction is
 * committed by the application role.
 */
@ApiTest
class WithdrawalRequestRowLevelSecurityTest {

    private static final String RLS_VIOLATION = "row-level security";
    private static final String EMAIL = "ana@example.com";
    private static final String MATCH = "select order_id from lock_withdrawable_order(?, ?)";
    private static final String INSERT_REQUEST = """
        insert into withdrawal_request (id, order_id, code, status, out_of_term, created_at, resolved_at)
        values (?, ?, ?, ?, false, now(), ?)
        """;
    private static final String INSERT_EMAIL = """
        insert into email_outbox (id, type, reference, version, status, attempts, next_attempt_at, created_at,
                                  updated_at)
        values (gen_random_uuid(), ?, ?, ?, ?, ?, now(), now(), now())
        """;
    private static final String RESOLVE_ALL = "update withdrawal_request set status = 'RESOLVED', resolved_at = now()";

    /** Who runs the transaction ({@code actor} empty: no security context). */
    record Caller(String name, Optional<Actor> actor) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Those who may send the form: the guest or the customer browsing the storefront. */
    static Stream<Caller> buyers() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A)),
            new Caller("customer B", Optional.of(CUSTOMER_B)),
            new Caller("guest", Optional.of(GUEST)),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty()))));
    }

    /** Those the form never answers. */
    static Stream<Caller> notBuyers() {
        return Stream.of(
            new Caller("administrator", Optional.of(ADMIN)),
            new Caller("system", Optional.of(SYSTEM)),
            new Caller("no context", Optional.empty()));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final OrderId first = OrderId.of(UUID.randomUUID());
    private final OrderId second = OrderId.of(UUID.randomUUID());
    private final OrderId withoutRequest = OrderId.of(UUID.randomUUID());
    private final UUID firstRequest = UUID.randomUUID();
    private final UUID secondRequest = UUID.randomUUID();

    WithdrawalRequestRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                          PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /**
     * Three paid orders of {@link #EMAIL}, the first of customer A: the first two with a request still to resolve and
     * its two emails, the third without any.
     */
    @BeforeEach
    void setUp() {
        for (OrderId order : List.of(first, second, withoutRequest)) {
            OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());
        }
        owner.update("update orders set user_id = ? where id = ?", CUSTOMER_A_ID.value(), first.value());
        owner.update(INSERT_REQUEST, firstRequest, first.value(), "ARR-22222222", "RECEIVED", null);
        owner.update(INSERT_REQUEST, secondRequest, second.value(), "ARR-33333333", "RECEIVED", null);
        for (UUID request : List.of(firstRequest, secondRequest)) {
            for (String type : List.of("WITHDRAWAL_RECEIVED_CUSTOMER", "WITHDRAWAL_RECEIVED_ADMIN")) {
                owner.update(INSERT_EMAIL, type, request, 0, "PENDING", 0);
            }
        }
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox, withdrawal_request, order_tracking_token, order_item, orders");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyers")
    void givenABuyerThatMatchedNoOrder_whenItReadsTheRequestsAndTheirEmails_thenItSeesNone(Caller caller) {
        List<UUID> requests = caller.run(rls, sql -> sql.queryForList("select id from withdrawal_request", UUID.class));
        List<UUID> emails = caller.run(rls, sql -> sql.queryForList("select reference from email_outbox", UUID.class));

        assertThat(requests).as("not even customer A, owner of the first order").isEmpty();
        assertThat(emails).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyers")
    void givenABuyerThatMatchedTheFirstOrder_whenItReadsTheRequestsAndTheirEmails_thenItSeesOnlyThatOrders(
        Caller caller) {
        List<UUID> requests = caller.run(rls, sql -> {
            match(sql, first);
            return sql.queryForList("select id from withdrawal_request", UUID.class);
        });
        List<UUID> emails = caller.run(rls, sql -> {
            match(sql, first);
            return sql.queryForList("select reference from email_outbox", UUID.class);
        });

        assertThat(requests).containsExactly(firstRequest);
        assertThat(emails).containsExactly(firstRequest, firstRequest);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyers")
    void givenABuyerWithTheWrongEmail_whenItMatchesTheFirstOrder_thenNothingMatchesAndItSeesNoRequest(Caller caller) {
        List<UUID> requests = caller.run(rls, sql -> {
            assertThat(sql.queryForList(MATCH, UUID.class, number(first), "otra@example.com")).isEmpty();
            return sql.queryForList("select id from withdrawal_request", UUID.class);
        });

        assertThat(requests).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notBuyers")
    void givenSomeoneTheFormNeverAnswers_whenItMatchesTheFirstOrder_thenNothingMatches(Caller caller) {
        String matchedOrder = caller.run(rls, sql -> {
            assertThat(sql.queryForList(MATCH, UUID.class, number(first), EMAIL)).isEmpty();
            return sql.queryForObject("select app_withdrawal_order_id()::text", String.class);
        });

        assertThat(matchedOrder).isNull();
    }

    @Test
    void givenTheRequestsOfTwoOrders_whenTheAdministratorOrTheSystemReadThem_thenTheySeeThemAll() {
        for (Actor staff : List.of(ADMIN, SYSTEM)) {
            List<UUID> requests = rls.as(staff,
                sql -> sql.queryForList("select id from withdrawal_request", UUID.class));

            assertThat(requests).as(staff.toString()).containsExactlyInAnyOrder(firstRequest, secondRequest);
        }
        List<UUID> withoutContext = rls.withoutContext(
            sql -> sql.queryForList("select id from withdrawal_request", UUID.class));

        assertThat(withoutContext).as("no context").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyers")
    void givenABuyerThatMatchedAnOrder_whenItRecordsARequestAndItsEmails_thenTheyAreAccepted(Caller caller) {
        UUID request = UUID.randomUUID();

        caller.run(rls, sql -> {
            match(sql, withoutRequest);
            sql.update(INSERT_REQUEST, request, withoutRequest.value(), "ARR-44444444", "RECEIVED", null);
            sql.update(INSERT_EMAIL, "WITHDRAWAL_RECEIVED_CUSTOMER", request, 0, "PENDING", 0);
            return sql.update(INSERT_EMAIL, "WITHDRAWAL_RECEIVED_ADMIN", request, 0, "PENDING", 0);
        });

        assertThat(owner.queryForObject("select order_id from withdrawal_request where id = ?", UUID.class, request))
            .isEqualTo(withoutRequest.value());
        assertThat(owner.queryForList("select type from email_outbox where reference = ?", String.class, request))
            .containsExactlyInAnyOrder("WITHDRAWAL_RECEIVED_CUSTOMER", "WITHDRAWAL_RECEIVED_ADMIN");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyers")
    void givenABuyerThatMatchedNoOrder_whenItRecordsARequest_thenItIsRefused(Caller caller) {
        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update(INSERT_REQUEST, UUID.randomUUID(),
            withoutRequest.value(), "ARR-44444444", "RECEIVED", null)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @Test
    void givenTheGuestMatchedAnOrder_whenItRecordsARequestOfAnotherOrder_thenItIsRefused() {
        OrderId another = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, another, OrderStatus.PAID, Instant.now());

        assertThatThrownBy(() -> rls.as(GUEST, sql -> {
            match(sql, withoutRequest);
            return sql.update(INSERT_REQUEST, UUID.randomUUID(), another.value(), "ARR-44444444", "RECEIVED", null);
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @Test
    void givenTheGuestMatchedAnOrder_whenItRecordsTheRequestAlreadyResolved_thenItIsRefused() {
        assertThatThrownBy(() -> rls.as(GUEST, sql -> {
            match(sql, withoutRequest);
            return sql.update(INSERT_REQUEST, UUID.randomUUID(), withoutRequest.value(), "ARR-44444444", "RESOLVED",
                Timestamp.from(Instant.now().plusSeconds(60)));
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notBuyers")
    void givenSomeoneTheFormNeverAnswers_whenItRecordsARequest_thenItIsRefused(Caller caller) {
        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update(INSERT_REQUEST, UUID.randomUUID(),
            withoutRequest.value(), "ARR-44444444", "RECEIVED", null)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @Test
    void givenTheGuestMatchedTheFirstOrder_whenItEnqueuesTheEmailsOfAnotherOrdersRequest_thenItIsRefused() {
        assertThatThrownBy(() -> rls.as(GUEST, sql -> {
            match(sql, first);
            return sql.update(INSERT_EMAIL, "WITHDRAWAL_RECEIVED_CUSTOMER", secondRequest, 0, "PENDING", 0);
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    /** Type, version, status and attempts of a row the policy does not admit, for the guest's own request. */
    @ParameterizedTest
    @ValueSource(strings = {"ORDER_PAID_CUSTOMER,0,PENDING,0", "ORDER_PAID_ADMIN,0,PENDING,0",
        "WITHDRAWAL_RECEIVED_CUSTOMER,1,PENDING,0", "WITHDRAWAL_RECEIVED_ADMIN,0,SENT,0",
        "WITHDRAWAL_RECEIVED_CUSTOMER,0,PENDING,1"})
    void givenTheGuestRecordedARequest_whenItEnqueuesAnythingButItsTwoNewEmails_thenItIsRefused(String row) {
        String[] values = row.split(",");
        UUID request = UUID.randomUUID();

        assertThatThrownBy(() -> rls.as(GUEST, sql -> {
            match(sql, withoutRequest);
            sql.update(INSERT_REQUEST, request, withoutRequest.value(), "ARR-44444444", "RECEIVED", null);
            return sql.update(INSERT_EMAIL, values[0], request, Integer.parseInt(values[1]), values[2],
                Integer.parseInt(values[3]));
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @Test
    void givenTheRequestsOfTwoOrders_whenTheAdministratorResolvesThem_thenBothAreResolved() {
        int resolved = rls.as(ADMIN, sql -> sql.update(RESOLVE_ALL));

        assertThat(resolved).isEqualTo(2);
        assertThat(owner.queryForList("select status from withdrawal_request", String.class)).containsOnly("RESOLVED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyers")
    void givenABuyerThatMatchedTheFirstOrder_whenItResolvesTheRequests_thenNoneChanges(Caller caller) {
        int resolved = caller.run(rls, sql -> {
            match(sql, first);
            return sql.update(RESOLVE_ALL);
        });

        assertThat(resolved).isZero();
        assertThat(owner.queryForList("select status from withdrawal_request", String.class)).containsOnly("RECEIVED");
    }

    @Test
    void givenTheSystemOrNoContext_whenTheyResolveTheRequests_thenNoneChanges() {
        int bySystem = rls.as(SYSTEM, sql -> sql.update(RESOLVE_ALL));
        int withoutContext = rls.withoutContext(sql -> sql.update(RESOLVE_ALL));

        assertThat(bySystem).as("system").isZero();
        assertThat(withoutContext).as("no context").isZero();
        assertThat(owner.queryForList("select status from withdrawal_request", String.class)).containsOnly("RECEIVED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyone")
    void givenTheRequestsOfTwoOrders_whenAnyoneDeletesThem_thenItIsDenied(Caller caller) {
        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update("delete from withdrawal_request")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThat(owner.queryForObject("select count(*) from withdrawal_request", Integer.class)).isEqualTo(2);
    }

    static Stream<Caller> everyone() {
        return Stream.concat(buyers(), notBuyers());
    }

    /** What the form does first: locks {@code order} by its number and {@link #EMAIL}, as the transaction's order. */
    private void match(JdbcTemplate sql, OrderId order) {
        assertThat(sql.queryForList(MATCH, UUID.class, number(order), EMAIL)).containsExactly(order.value());
    }

    private long number(OrderId order) {
        return OrderRows.numberOf(owner, order);
    }
}
