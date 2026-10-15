package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.PaidOrderDetails;
import com.pijamaparty.store.ordering.application.PaidOrderDetailsContractTest;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.application.SaveTrackingLinks;
import com.pijamaparty.store.ordering.application.TrackedOrders;
import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders placed by their buyers; their details read and their email links added by the system, each in its own
 * committed transaction, and the links followed by a guest holding them, so Row Level Security applies as in
 * production (RNF-27).
 */
@ApiTest
class JdbcPaidOrderDetailsContractTest
    extends PaidOrderDetailsContractTest<JdbcPaidOrderDetailsContractTest.AsActors> {

    private final AsActors asActors;
    private final JdbcTemplate owner;
    private final RlsPolicyTester rls;

    JdbcPaidOrderDetailsContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                     JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.asActors = new AsActors(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
    }

    @Override
    protected AsActors createSubject() {
        return asActors;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asActors.tokens().put(token.hash(), token);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAGuestOrACustomer_whenReadingTheDetailsOfAnOrder_thenRowLevelSecurityHidesThem() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());

        for (Actor actor : new Actor[] {RlsPolicyTester.GUEST, RlsPolicyTester.CUSTOMER_A}) {
            Optional<PaidOrder> details = CurrentActor.callAs(actor, () -> asActors.transactions()
                .execute(status -> asActors.adapter().detailsOf(order)));

            assertThat(details).isEmpty();
        }
    }

    @Test
    void givenAGuest_whenAddingALinkToAnOrder_thenRowLevelSecurityRefusesIt() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());
        String hash = newToken().hash();

        // The table only takes the system's rows, and a buyer's for the order of one of its own checkout keys
        // (T-E3-14).
        assertThatThrownBy(() -> CurrentActor.callAs(RlsPolicyTester.GUEST, () -> asActors.transactions()
            .execute(status -> asActors.adapter().add(order, hash, Instant.now()))))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThat(owner.queryForObject("select count(*) from order_tracking_token", Long.class)).isZero();
    }

    @Test
    void givenATrackingTokenRow_whenAGuestHoldingAnotherTokenReadsTheTable_thenItSeesNothing() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());
        subject.add(order, newToken().hash(), Instant.now());

        for (Actor actor : new Actor[] {RlsPolicyTester.GUEST, RlsPolicyTester.CUSTOMER_A, RlsPolicyTester.CUSTOMER_B,
            RlsPolicyTester.ADMIN}) {
            assertThat(tokensSeenBy(actor)).as(actor.toString()).isZero();
        }
        assertThat(tokensSeenBy(RlsPolicyTester.SYSTEM)).isOne();
    }

    @Test
    void givenACustomerHoldingTheEmailLinkOfAnotherCustomersOrder_whenLookingForIt_thenOnlyThatOrderIsAnswered() {
        OrderId ofA = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, ofA, OrderStatus.PAID, Instant.now());
        owner.update("update orders set user_id = ? where id = ?", RlsPolicyTester.CUSTOMER_A_ID.value(),
            ofA.value());
        TrackingToken emailed = newToken();
        subject.add(ofA, emailed.hash(), Instant.now());
        var customerB = Actor.customer(RlsPolicyTester.CUSTOMER_B_ID, Optional.empty(), Optional.of(emailed));
        var customerBWithAnotherLink = Actor.customer(RlsPolicyTester.CUSTOMER_B_ID, Optional.empty(),
            Optional.of(newToken()));

        Optional<TrackedOrder> found = CurrentActor.callAs(customerB, () -> asActors.adapter().find(emailed));
        Optional<TrackedOrder> notFound =
            CurrentActor.callAs(customerBWithAnotherLink, () -> asActors.adapter().find(emailed));

        assertThat(found.map(TrackedOrder::id)).contains(ofA);
        assertThat(notFound).isEmpty();
    }

    private long tokensSeenBy(Actor actor) {
        Long count = rls.as(actor, sql -> sql.queryForObject("select count(*) from order_tracking_token", Long.class));
        return count == null ? 0 : count;
    }

    @Test
    void givenATrackingTokenRow_whenTheSystemTriesToChangeOrDeleteIt_thenItIsRefused() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());
        subject.add(order, newToken().hash(), Instant.now());

        for (String change : new String[] {"update order_tracking_token set created_at = now()",
            "delete from order_tracking_token"}) {
            assertThatThrownBy(() -> rls.as(RlsPolicyTester.SYSTEM, sql -> sql.update(change))).as(change)
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        }
    }

    /**
     * Inserts as the order's buyer; reads the details and adds the links as the system; finds as a guest holding the
     * token. Each call in its own committed transaction.
     */
    record AsActors(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, TrackedOrders, PaidOrderDetails, SaveTrackingLinks {

        @Override
        public Optional<TrackedOrder> find(TrackingToken token) {
            return CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.of(token)),
                () -> adapter.find(token));
        }

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            CurrentActor.runAs(Actor.anonymous(Optional.empty(), Optional.of(token)),
                () -> transactions.executeWithoutResult(status -> adapter.insert(order)));
        }

        @Override
        public long next() {
            Long number = CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.next()));
            return number == null ? 0 : number;
        }

        @Override
        public Optional<PaidOrder> detailsOf(OrderId order) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.detailsOf(order)));
        }

        @Override
        public boolean add(OrderId order, String tokenHash, Instant at) {
            return Boolean.TRUE.equals(CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.add(order, tokenHash, at))));
        }
    }
}
