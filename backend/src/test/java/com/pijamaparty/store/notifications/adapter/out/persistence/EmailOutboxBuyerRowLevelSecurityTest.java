package com.pijamaparty.store.notifications.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * T-E3-23 (UC-17, RNF-27): the buyer's policies of {@code email_outbox} (V24), with customer A, customer B, a guest,
 * the administrator and the system, plus a missing context, a guest without a token and a customer holding a guest's
 * token. A guest or a customer reaches only the buyer's confirmations of an order it can see: it reads them, enqueues
 * a new version still to send, and locks one not attempted yet without changing it. The rest of the matrix is
 * {@link EmailOutboxRowLevelSecurityTest}. Every transaction is committed by the application role.
 */
@ApiTest
class EmailOutboxBuyerRowLevelSecurityTest {

    private static final String RLS_VIOLATION = "row-level security";
    private static final String INSERT = """
        insert into email_outbox (id, type, reference, version, status, attempts, next_attempt_at, created_at,
                                  updated_at)
        values (gen_random_uuid(), ?, ?, ?, ?, ?, now(), now(), now())
        """;

    /** Who runs the transaction ({@code actor} empty: no security context), and whose orders it sees. */
    record Caller(String name, Optional<Actor> actor, Set<String> sees) {

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
            new Caller("customer A", Optional.of(CUSTOMER_A), Set.of("A")),
            new Caller("customer B", Optional.of(CUSTOMER_B), Set.of()),
            new Caller("guest", Optional.of(GUEST), Set.of("guest")),
            new Caller("no context", Optional.empty(), Set.of()),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty())),
                Set.of()),
            new Caller("customer A holding the guest's token",
                Optional.of(Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.of(GUEST_TRACKING))),
                Set.of("A", "guest")));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final OrderId guestsOrder = OrderId.of(UUID.randomUUID());
    private final OrderId customerAsOrder = OrderId.of(UUID.randomUUID());

    EmailOutboxBuyerRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                         PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /**
     * A paid order of the guest's tracking token and one of customer A, each with its buyer's confirmation pending and
     * not attempted, and its administrator's notice.
     */
    @BeforeEach
    void setUp() {
        OrderRows.insert(owner, guestsOrder, OrderStatus.PAID, Instant.now());
        owner.update("update orders set tracking_token_hash = ? where id = ?", GUEST_TRACKING.hash(),
            guestsOrder.value());
        OrderRows.insert(owner, customerAsOrder, OrderStatus.PAID, Instant.now());
        owner.update("update orders set user_id = ? where id = ?", CUSTOMER_A_ID.value(), customerAsOrder.value());
        for (OrderId order : List.of(guestsOrder, customerAsOrder)) {
            for (String type : List.of("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN")) {
                owner.update(INSERT, type, order.value(), 0, "PENDING", 0);
            }
        }
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox, withdrawal_request, order_tracking_token, order_item, orders");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTheConfirmationsOfTwoOrders_whenABuyerReadsThem_thenItSeesOnlyTheBuyersOfTheOrdersItSees(
        Caller caller) {
        var visible = caller.run(rls, sql -> sql.queryForList(
            "select reference from email_outbox where type = 'ORDER_PAID_CUSTOMER'", UUID.class));
        var notices = caller.run(rls, sql -> sql.queryForList(
            "select reference from email_outbox where type = 'ORDER_PAID_ADMIN'", UUID.class));

        assertThat(visible).containsExactlyInAnyOrderElementsOf(ordersSeenBy(caller));
        assertThat(notices).as("the administrator's notices").isEmpty();
    }

    @Test
    void givenTheConfirmationsOfTwoOrders_whenStaffReadThem_thenTheySeeThemAll() {
        for (Actor staff : List.of(ADMIN, SYSTEM)) {
            Integer count = rls.as(staff,
                sql -> sql.queryForObject("select count(*) from email_outbox", Integer.class));

            assertThat(count).as(staff.toString()).isEqualTo(4);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTheGuestsOrder_whenABuyerEnqueuesItsConfirmationAgain_thenOnlyOneThatSeesItIsAccepted(Caller caller) {
        var enqueue = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT, "ORDER_PAID_CUSTOMER",
            guestsOrder.value(), 1, "PENDING", 0);

        if (caller.sees().contains("guest")) {
            assertThat(caller.run(rls, enqueue)).isOne();
        } else {
            assertThatThrownBy(() -> caller.run(rls, enqueue))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_PAID_ADMIN,1,PENDING,0", "ORDER_PAID_CUSTOMER,0,PENDING,0",
        "ORDER_PAID_CUSTOMER,1,SENT,0", "ORDER_PAID_CUSTOMER,1,PENDING,1"})
    void givenTheGuestsOrder_whenTheGuestEnqueuesAnythingButANewVersionOfItsConfirmation_thenItIsRefused(
        String row) {
        String[] values = row.split(",");

        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(INSERT, values[0], guestsOrder.value(),
            Integer.parseInt(values[1]), values[2], Integer.parseInt(values[3]))))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTheConfirmationsOfTwoOrders_whenABuyerLocksThem_thenItLocksOnlyThoseOfTheOrdersItSees(Caller caller) {
        var locked = caller.run(rls, sql -> sql.queryForList(
            "select reference from email_outbox where type = 'ORDER_PAID_CUSTOMER' for update", UUID.class));

        assertThat(locked).containsExactlyInAnyOrderElementsOf(ordersSeenBy(caller));
    }

    @Test
    void givenAConfirmationAlreadyAttempted_whenTheGuestLocksIt_thenItIsNotLockedButStillRead() {
        owner.update("update email_outbox set attempts = 1 where reference = ?", guestsOrder.value());

        List<UUID> locked = rls.as(GUEST,
            sql -> sql.queryForList("select id from email_outbox for update", UUID.class));
        List<UUID> read = rls.as(GUEST, sql -> sql.queryForList("select id from email_outbox", UUID.class));

        assertThat(locked).isEmpty();
        assertThat(read).hasSize(1);
    }

    @Test
    void givenTheGuestsConfirmation_whenTheGuestUpdatesIt_thenItIsRefused() {
        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(
            "update email_outbox set status = 'FAILED', updated_at = now() where reference = ?", guestsOrder.value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThat(owner.queryForList("select status from email_outbox where reference = ?", String.class,
            guestsOrder.value())).containsOnly("PENDING");
    }

    private List<UUID> ordersSeenBy(Caller caller) {
        return Stream.of("A", "guest").filter(caller.sees()::contains)
            .map(order -> order.equals("A") ? customerAsOrder.value() : guestsOrder.value())
            .toList();
    }
}
