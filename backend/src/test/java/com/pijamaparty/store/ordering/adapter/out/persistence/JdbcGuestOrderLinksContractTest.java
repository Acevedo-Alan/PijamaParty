package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.CustomerOrderPages;
import com.pijamaparty.store.ordering.application.CustomerOrderSummary;
import com.pijamaparty.store.ordering.application.GuestOrderLinks;
import com.pijamaparty.store.ordering.application.GuestOrderLinksContractTest;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Guest orders placed by their buyers and linked, each in a committed transaction, as {@code SYSTEM}, as the sign-in
 * runs, so Row Level Security and the update guard of {@code orders} (V33) apply as in production (RNF-27). Read back
 * as the customer itself.
 */
@ApiTest
class JdbcGuestOrderLinksContractTest extends GuestOrderLinksContractTest<JdbcGuestOrderLinksContractTest.AsSystem> {

    private static final String PASSWORD_HASH =
        "{bcrypt}$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final AsSystem asSystem;
    private final JdbcTemplate owner;

    JdbcGuestOrderLinksContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                    PostgreSQLContainer<?> postgres) {
        this.asSystem = new AsSystem(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asSystem.tokens().put(token.hash(), token);
    }

    @Override
    protected UserId verifiedCustomer(String email) {
        return customer(email, true);
    }

    @Override
    protected void unlinked(OrderId order) {
        owner.update("update orders set unlinked_at = now() where id = ?", order.value());
    }

    @Override
    protected void emailCorrected(OrderId order, String email) {
        owner.update("update orders set customer_email = ? where id = ?", email, order.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders, address, user_token, "
            + "admin_recovery_code, app_user");
    }

    @Test
    void givenAGuestOrderInAnyStatus_whenLinked_thenItJoinsAndOnlyItsUserAndUpdateInstantChange() {
        UserId ana = verifiedCustomer(ANA);
        var order = placed(Optional.empty(), ANA);
        owner.update("update orders set status = 'CANCELLED' where id = ?", order.id().value());
        Map<String, Object> before = rowOf(order.id());

        assertThat(asSystem.link(ana, ANA, NOW)).isOne();

        Map<String, Object> after = rowOf(order.id());
        assertThat(after.get("user_id")).isEqualTo(ana.value());
        assertThat(((Timestamp) after.get("updated_at")).toInstant()).isEqualTo(NOW);
        before.keySet().removeAll(List.of("user_id", "updated_at"));
        after.keySet().removeAll(List.of("user_id", "updated_at"));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void givenACustomerWhoseEmailIsNotVerified_whenTheSystemLinksItsEmailsOrders_thenTheGuardRefusesIt() {
        UserId ana = customer(ANA, false);
        placed(Optional.empty(), ANA);

        assertThatThrownBy(() -> asSystem.link(ana, ANA, NOW))
            .hasStackTraceContaining("only the system links a guest order");
        assertThat(owner.queryForObject("select count(*) from orders where user_id is not null", Integer.class))
            .isZero();
    }

    @Test
    void givenAnotherCustomersEmail_whenTheSystemLinksItsOrdersToThisCustomer_thenTheGuardRefusesIt() {
        UserId ana = verifiedCustomer(ANA);
        placed(Optional.empty(), "bruno@example.com");

        assertThatThrownBy(() -> asSystem.link(ana, "bruno@example.com", NOW))
            .hasStackTraceContaining("only the system links a guest order");
    }

    private UserId customer(String email, boolean verified) {
        UserId customer = UserId.of(UUID.randomUUID());
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, ?, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, customer.value(), email, verified, PASSWORD_HASH);
        return customer;
    }

    private Map<String, Object> rowOf(OrderId order) {
        return owner.queryForMap("select * from orders where id = ?", order.value());
    }

    /** Each call in its own committed transaction: inserts as the buyer, links as the system, reads as the customer. */
    record AsSystem(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, GuestOrderLinks, CustomerOrderPages {

        @Override
        public int link(UserId customer, String email, Instant at) {
            Integer linked = CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.link(customer, email, at)));
            return linked == null ? 0 : linked;
        }

        @Override
        public List<CustomerOrderSummary> page(UserId customer, int offset, int limit) {
            return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.page(customer, offset, limit)));
        }

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            Actor buyer = order.userId()
                .map(user -> Actor.customer(user, Optional.empty(), Optional.of(token)))
                .orElseGet(() -> Actor.anonymous(Optional.empty(), Optional.of(token)));
            CurrentActor.runAs(buyer, () -> transactions.executeWithoutResult(status -> adapter.insert(order)));
        }

        @Override
        public long next() {
            Long number = CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.next()));
            return number == null ? 0 : number;
        }
    }
}
