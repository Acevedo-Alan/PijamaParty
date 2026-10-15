package com.pijamaparty.store.ordering.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_CART;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-14 (RNF-27, RNF-29.6): the policy {@code order_tracking_token_repeat_insert} (V21). A buyer that repeats its
 * checkout adds the tracking token it holds only to the order of one of its own idempotency keys. The full matrix of
 * the ordering tables is {@link OrderingRowLevelSecurityTest} (T-E3-21).
 */
@ApiTest
class RepeatedCheckoutTrackingRowLevelSecurityTest {

    private static final String INSERT_TOKEN = """
        insert into order_tracking_token (token_hash, order_id, created_at) values (?, ?, now())
        """;
    private static final String RLS_VIOLATION = "row-level security";

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final OrderId guestsOrder = OrderId.of(UUID.randomUUID());
    private final OrderId otherOrder = OrderId.of(UUID.randomUUID());

    RepeatedCheckoutTrackingRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                                 PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Two orders; the guest's cart placed the first with a key, customer A the second. */
    @BeforeEach
    void seed() {
        Instant expiry = Instant.now().plusSeconds(1800);
        OrderRows.insert(owner, guestsOrder, OrderStatus.AWAITING_PAYMENT, expiry);
        OrderRows.insert(owner, otherOrder, OrderStatus.AWAITING_PAYMENT, expiry);
        key("CART:" + GUEST_CART.hash(), guestsOrder);
        key("USER:" + CUSTOMER_A_ID.value(), otherOrder);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key, order_tracking_token, order_item, orders");
    }

    @Test
    void givenTheGuestsKey_whenTheGuestAddsTheTokenItHoldsToThatOrder_thenItIsAcceptedAndOpensTheOrder() {
        int added = rls.as(GUEST, sql -> sql.update(INSERT_TOKEN, GUEST_TRACKING.hash(), guestsOrder.value()));
        List<UUID> visible = rls.as(GUEST, sql -> sql.queryForList("select id from orders", UUID.class));

        assertThat(added).isOne();
        assertThat(visible).containsExactly(guestsOrder.value());
    }

    @Test
    void givenAnOrderOfAnotherScopesKey_whenTheGuestAddsItsTokenToIt_thenItIsRejected() {
        assertRejected(GUEST, GUEST_TRACKING.hash(), otherOrder);
    }

    @Test
    void givenTheGuestsKey_whenTheGuestAddsATokenItDoesNotHold_thenItIsRejected() {
        assertRejected(GUEST, new TrackingToken("a-token-the-guest-does-not-hold").hash(), guestsOrder);
    }

    @Test
    void givenTheGuestsKey_whenAnotherCustomerOrTheAdministratorAddsATokenToItsOrder_thenItIsRejected() {
        TrackingToken held = new TrackingToken("customer-b-tracking-token");
        Actor customerB = Actor.customer(CUSTOMER_B.userId().orElseThrow(), Optional.empty(), Optional.of(held));

        assertRejected(customerB, held.hash(), guestsOrder);
        assertRejected(CUSTOMER_B, GUEST_TRACKING.hash(), guestsOrder);
        assertRejected(ADMIN, GUEST_TRACKING.hash(), guestsOrder);
    }

    private void assertRejected(Actor actor, String tokenHash, OrderId order) {
        assertThatThrownBy(() -> rls.as(actor, sql -> sql.update(INSERT_TOKEN, tokenHash, order.value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThat(owner.queryForObject("select count(*) from order_tracking_token", Integer.class)).isZero();
    }

    private void key(String scope, OrderId order) {
        owner.update("""
            insert into idempotency_key (scope, idempotency_key, request_hash, resource_id, response_status,
                                         response_body, created_at, expires_at)
            values (?, ?, ?, ?, 201, '{}', now(), now() + interval '24 hours')
            """, scope, UUID.randomUUID(), "e".repeat(64), order.value());
    }
}
