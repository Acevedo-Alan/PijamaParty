package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.ConfirmableOrders;
import com.pijamaparty.store.ordering.application.ConfirmableOrdersContractTest;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Each call in its own transaction as {@code SYSTEM}, as the payment's transaction runs (RNF-27). */
@ApiTest
class JdbcConfirmableOrdersContractTest extends ConfirmableOrdersContractTest<ConfirmableOrders> {

    private final JdbcOrderStore adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    JdbcConfirmableOrdersContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected ConfirmableOrders createSubject() {
        return as(Actor.system());
    }

    @Override
    protected long givenOrder(OrderId order, OrderStatus status, Optional<UUID> couponId) {
        if (couponId.isPresent()) {
            return OrderRows.insertWithCoupon(owner, order, status, EXPIRES, couponId.get());
        }
        OrderRows.insert(owner, order, status, EXPIRES);
        return OrderRows.numberOf(owner, order);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAGuestWithoutTheOrdersToken_whenLockingTheOrder_thenRowLevelSecurityHidesIt() {
        OrderId order = OrderId.of(UUID.randomUUID());
        givenOrder(order, OrderStatus.AWAITING_PAYMENT, Optional.empty());

        assertThat(as(Actor.anonymous(Optional.empty(), Optional.empty())).lock(order)).isEmpty();
    }

    private ConfirmableOrders as(Actor actor) {
        return order -> CurrentActor.callAs(actor, () -> transactions.execute(status -> adapter.lock(order)));
    }
}
