package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.application.TrackedOrders;
import com.pijamaparty.store.ordering.application.TrackedOrdersContractTest;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
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
 * Orders placed by their buyers and looked for, in the adapter's own transaction, by a guest that holds the token, as
 * the tracking link runs, so Row Level Security applies as in production (RNF-27). Shipped as the owner.
 */
@ApiTest
class JdbcTrackedOrdersContractTest extends TrackedOrdersContractTest<JdbcTrackedOrdersContractTest.AsBuyer> {

    private final AsBuyer asBuyer;
    private final JdbcTemplate owner;

    JdbcTrackedOrdersContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                  PostgreSQLContainer<?> postgres) {
        this.asBuyer = new AsBuyer(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsBuyer createSubject() {
        return asBuyer;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asBuyer.tokens().put(token.hash(), token);
    }

    @Override
    protected void shipped(OrderId order, String shipmentTrackingNumber) {
        owner.update("update orders set status = 'SHIPPED', shipment_tracking_number = ? where id = ?",
            shipmentTrackingNumber, order.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate order_tracking_token, order_item, orders");
    }

    @Test
    void givenAGuestHoldingTheTokenOfAnotherOrder_whenLookingForThisOne_thenRowLevelSecurityHidesIt() {
        TrackingToken token = newToken();
        subject.insert(newOrder(token, Optional.empty(), Optional.empty()));
        var stranger = Actor.anonymous(Optional.empty(), Optional.of(newToken()));

        assertThat(CurrentActor.callAs(stranger, () -> asBuyer.adapter().find(token))).isEmpty();
    }

    @Test
    void givenACustomerWhoSeesBothItsOrders_whenLookingForOneByItsToken_thenOnlyThatOneIsAnswered() {
        UserId customer = UserId.of(UUID.randomUUID());
        TrackingToken first = newToken();
        TrackingToken second = newToken();
        subject.insert(newOrder(first, Optional.of(customer), Optional.empty()));
        var secondOrder = newOrder(second, Optional.of(customer), Optional.empty());
        subject.insert(secondOrder);
        var holdingTheSecond = Actor.customer(customer, Optional.empty(), Optional.of(second));

        assertThat(CurrentActor.callAs(holdingTheSecond, () -> asBuyer.adapter().find(second)))
            .map(TrackedOrders.TrackedOrder::id).contains(secondOrder.id());
    }

    /** Each call in its own committed transaction: inserts as the order's buyer, finds as a guest with the token. */
    record AsBuyer(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, TrackedOrders {

        @Override
        public Optional<TrackedOrder> find(TrackingToken token) {
            return CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.of(token)),
                () -> adapter.find(token));
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
