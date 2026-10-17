package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.CustomerOrderPages;
import com.pijamaparty.store.ordering.application.CustomerOrderSummary;
import com.pijamaparty.store.ordering.application.CustomerOrderViews;
import com.pijamaparty.store.ordering.application.CustomerOrdersContractTest;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders placed by their buyers and read, each in a committed transaction, by the customer itself, as "Mis pedidos"
 * runs, so Row Level Security applies as in production (RNF-27). Shipped as the owner.
 */
@ApiTest
class JdbcCustomerOrdersContractTest extends CustomerOrdersContractTest<JdbcCustomerOrdersContractTest.AsCustomer> {

    private final AsCustomer asCustomer;
    private final JdbcOrderStore adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcCustomerOrdersContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                   PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.asCustomer = new AsCustomer(adapter, transactions, new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsCustomer createSubject() {
        return asCustomer;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asCustomer.tokens().put(token.hash(), token);
    }

    @Override
    protected void shipped(OrderId order, String shipmentTrackingNumber) {
        owner.update("update orders set status = 'SHIPPED', shipment_tracking_number = ? where id = ?",
            shipmentTrackingNumber, order.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenACustomerHoldingTheTrackingTokenOfAGuestOrder_whenListed_thenThatOrderIsNotAmongItsOrders() {
        var guests = placed(Optional.empty(), NOW, Optional.empty());
        TrackingToken guestsToken = asCustomer.tokens().get(guests.trackingTokenHash());
        var anas = placed(Optional.of(ana), NOW, Optional.empty());
        var holdingIt = Actor.customer(ana, Optional.empty(), Optional.of(guestsToken));

        List<CustomerOrderSummary> listed = CurrentActor.callAs(holdingIt,
            () -> transactions.execute(status -> adapter.page(ana, 0, 20)));
        assertThat(listed).extracting(CustomerOrderSummary::id).containsExactly(anas.id());
        assertThat(CurrentActor.callAs(holdingIt, () -> adapter.find(ana, guests.id()))).isEmpty();
    }

    @Test
    void givenACustomerAskingWithAnotherCustomersId_whenListedOrFound_thenRowLevelSecurityHidesThoseOrders() {
        var anas = placed(Optional.of(ana), NOW, Optional.empty());
        var brunoAsking = Actor.customer(bruno, Optional.empty(), Optional.empty());

        List<CustomerOrderSummary> listed = CurrentActor.callAs(brunoAsking,
            () -> transactions.execute(status -> adapter.page(ana, 0, 20)));
        assertThat(listed).isEmpty();
        assertThat(CurrentActor.callAs(brunoAsking, () -> adapter.find(ana, anas.id()))).isEmpty();
    }

    /** Each call in its own committed transaction: inserts as the order's buyer, reads as the customer itself. */
    record AsCustomer(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, CustomerOrderPages, CustomerOrderViews {

        @Override
        public List<CustomerOrderSummary> page(UserId customer, int offset, int limit) {
            return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.page(customer, offset, limit)));
        }

        @Override
        public Optional<CustomerOrder> find(UserId customer, OrderId order) {
            return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
                () -> adapter.find(customer, order));
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
