package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.application.BuyerEmails;
import com.pijamaparty.store.ordering.application.CorrectableOrders;
import com.pijamaparty.store.ordering.application.CorrectableOrders.CorrectableOrder;
import com.pijamaparty.store.ordering.application.CorrectableOrdersContractTest;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.PaidOrderDetails;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.application.SaveTrackingLinks;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
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
 * Orders placed by their buyers, then locked and their email replaced by a guest holding the token, each in its own
 * committed transaction, as the email correction runs, so Row Level Security applies as in production (RNF-27).
 * Details read and links added as the system; shipped as the owner. The policies themselves are in
 * {@link OrderingRowLevelSecurityTest}.
 */
@ApiTest
class JdbcCorrectableOrdersContractTest
    extends CorrectableOrdersContractTest<JdbcCorrectableOrdersContractTest.AsActors> {

    private final AsActors asActors;
    private final JdbcTemplate owner;

    JdbcCorrectableOrdersContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.asActors = new AsActors(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsActors createSubject() {
        return asActors;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asActors.tokens().put(token.hash(), token);
    }

    @Override
    protected void shipped(OrderId order) {
        owner.update("update orders set status = 'SHIPPED', shipment_tracking_number = 'CA123456789AR' where id = ?",
            order.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate order_tracking_token, order_item, orders");
    }

    @Test
    void givenAGuestHoldingTheTokenOfAnotherOrder_whenLockingOrReplacingThisOne_thenRowLevelSecurityHidesIt() {
        TrackingToken token = newToken();
        OrderId order = placed(token);
        var stranger = Actor.anonymous(Optional.empty(), Optional.of(newToken()));

        Optional<CorrectableOrder> locked = CurrentActor.callAs(stranger, () -> asActors.transactions()
            .execute(status -> asActors.adapter().lock(token)));
        Boolean replaced = CurrentActor.callAs(stranger, () -> asActors.transactions()
            .execute(status -> asActors.adapter().replace(order, "otro@example.com", NOW)));

        assertThat(locked).isEmpty();
        assertThat(replaced).isFalse();
        assertThat(owner.queryForObject("select customer_email from orders where id = ?", String.class,
            order.value())).isEqualTo("ana.perez@gmial.com");
    }

    @Test
    void givenAnOrderLockedByItsBuyer_whenAnotherTransactionLocksItWithoutWaiting_thenItIsHeld() {
        TrackingToken token = newToken();
        OrderId order = placed(token);

        CurrentActor.runAs(Actor.anonymous(Optional.empty(), Optional.of(token)),
            () -> asActors.transactions().executeWithoutResult(status -> {
                assertThat(asActors.adapter().lock(token)).isPresent();
                assertThatThrownBy(() -> owner.queryForList(
                    "select id from orders where id = ? for update nowait", order.value()))
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining("could not obtain lock");
            }));
    }

    /** A paid order bound to the token, with a misspelled email, stored as the owner. */
    private OrderId placed(TrackingToken token) {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());
        owner.update("update orders set tracking_token_hash = ?, customer_email = 'ana.perez@gmial.com' where id = ?",
            token.hash(), order.value());
        return order;
    }

    /**
     * Inserts as the order's buyer; locks and replaces as a guest holding the token; reads the details and adds the
     * links as the system. Each call in its own committed transaction.
     */
    record AsActors(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens,
                    Map<OrderId, TrackingToken> checkoutTokens)
        implements SaveOrders, OrderNumbers, CorrectableOrders, BuyerEmails, PaidOrderDetails, SaveTrackingLinks {

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            checkoutTokens.put(order.id(), token);
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
        public Optional<CorrectableOrder> lock(TrackingToken token) {
            return CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.of(token)),
                () -> transactions.execute(status -> adapter.lock(token)));
        }

        @Override
        public boolean replace(OrderId order, String email, Instant at) {
            var holder = Actor.anonymous(Optional.empty(), Optional.ofNullable(checkoutTokens.get(order)));
            return Boolean.TRUE.equals(CurrentActor.callAs(holder,
                () -> transactions.execute(status -> adapter.replace(order, email, at))));
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
