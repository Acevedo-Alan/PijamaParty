package com.pijamaparty.store.ordering.adapter.out.persistence;

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
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
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
 * T-E3-23 (UC-17, RNF-27): {@code orders_buyer_email_update} and {@code orders_buyer_email_update_guard} (V24). A
 * guest or a customer updates only the order of the tracking token it holds, the checkout's or an email's, and only
 * its email; the administrator and the system still never change it. Every transaction is committed by the
 * application role.
 */
@ApiTest
class OrderEmailRowLevelSecurityTest {

    private static final String CORRECT_ALL = "update orders set customer_email = 'nuevo@example.com', "
        + "updated_at = now() returning id";

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

    static Stream<Caller> buyersWithoutTheGuestsToken() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A)),
            new Caller("customer B", Optional.of(CUSTOMER_B)),
            new Caller("no context", Optional.empty()),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty()))),
            new Caller("guest holding another token",
                Optional.of(Actor.anonymous(Optional.empty(), Optional.of(new TrackingToken("another-token"))))));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final OrderId guestsOrder = OrderId.of(UUID.randomUUID());
    private final OrderId customerAsOrder = OrderId.of(UUID.randomUUID());

    OrderEmailRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                   PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A paid order of the guest's tracking token, and a paid order of customer A under another token. */
    @BeforeEach
    void setUp() {
        OrderRows.insert(owner, guestsOrder, OrderStatus.PAID, Instant.now());
        owner.update("update orders set tracking_token_hash = ? where id = ?", GUEST_TRACKING.hash(),
            guestsOrder.value());
        OrderRows.insert(owner, customerAsOrder, OrderStatus.PAID, Instant.now());
        owner.update("update orders set user_id = ? where id = ?", CUSTOMER_A_ID.value(), customerAsOrder.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenTheGuestsToken_whenItCorrectsTheEmailOfEveryOrder_thenOnlyItsOwnChanges() {
        List<UUID> corrected = rls.as(GUEST, sql -> sql.queryForList(CORRECT_ALL, UUID.class));

        assertThat(corrected).containsExactly(guestsOrder.value());

        assertThat(emailOf(guestsOrder)).isEqualTo("nuevo@example.com");
        assertThat(emailOf(customerAsOrder)).isEqualTo("ana@example.com");
    }

    @Test
    void givenCustomerAHoldingTheGuestsToken_whenItCorrectsTheEmailOfEveryOrder_thenOnlyTheTokensOrderChanges() {
        var holding = Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.of(GUEST_TRACKING));

        List<UUID> corrected = rls.as(holding, sql -> sql.queryForList(CORRECT_ALL, UUID.class));

        assertThat(corrected)
            .as("its own order without the token is not corrected through the link")
            .containsExactly(guestsOrder.value());
    }

    @Test
    void givenTheLinkOfAConfirmationEmail_whenItsHolderCorrectsTheEmail_thenThatOrderChanges() {
        var emailed = new TrackingToken("emailed-tracking-token-for-tests");
        owner.update("insert into order_tracking_token (token_hash, order_id, created_at) values (?, ?, now())",
            emailed.hash(), customerAsOrder.value());

        List<UUID> corrected = rls.as(Actor.anonymous(Optional.empty(), Optional.of(emailed)),
            sql -> sql.queryForList(CORRECT_ALL, UUID.class));

        assertThat(corrected).containsExactly(customerAsOrder.value());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buyersWithoutTheGuestsToken")
    void givenNoTokenOfTheOrders_whenCorrectingTheirEmail_thenNothingChanges(Caller caller) {
        List<UUID> corrected = caller.run(rls, sql -> sql.queryForList(CORRECT_ALL, UUID.class));

        assertThat(corrected).isEmpty();

        assertThat(emailOf(guestsOrder)).isEqualTo("ana@example.com");
        assertThat(emailOf(customerAsOrder)).isEqualTo("ana@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"status = 'CANCELLED'", "customer_name = 'Otra'", "street = 'Otra'",
        "total_cents = 1, subtotal_cents = 1, shipping_cents = 0", "user_id = '00000000-000a-0000-0000-000000000001'",
        "tracking_token_hash = repeat('a', 64)", "shipment_tracking_number = 'X'"})
    void givenTheGuestsToken_whenItChangesAnythingButTheEmail_thenTheGuardRefusesIt(String change) {
        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update("update orders set " + change + " where id = ?",
            guestsOrder.value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("a buyer may only update the email");
    }

    @Test
    void givenTheAdministratorOrTheSystem_whenTheyCorrectTheBuyersEmail_thenTheirGuardStillRefusesIt() {
        for (Actor staff : List.of(ADMIN, SYSTEM)) {
            assertThatThrownBy(() -> rls.as(staff, sql -> sql.update(CORRECT_ALL.replace(" returning id", ""))))
                .as(staff.toString())
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("may only update the status");
        }
    }

    private String emailOf(OrderId order) {
        return owner.queryForObject("select customer_email from orders where id = ?", String.class, order.value());
    }
}
