package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.CustomerOrderExports;
import com.pijamaparty.store.ordering.application.CustomerOrderExportsContractTest;
import com.pijamaparty.store.ordering.application.ExportedOrder;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders placed by their buyers, each in a committed transaction, and exported in a read-only transaction as the
 * customer, as the data download does, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class JdbcCustomerOrderExportsContractTest
    extends CustomerOrderExportsContractTest<JdbcCustomerOrderExportsContractTest.AsCustomer> {

    private final AsCustomer asCustomer;
    private final JdbcTemplate owner;

    JdbcCustomerOrderExportsContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                         PostgreSQLContainer<?> postgres) {
        var transactions = new TransactionTemplate(transactionManager);
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
    protected UserId customer(String email) {
        return UserId.of(UUID.randomUUID());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenADeliveredOrderWithACouponAndATrackingNumber_whenExporting_thenTheyAreInTheExport() {
        UserId ana = customer("ana@example.com");
        var order = placed(ana, NOW);
        owner.update("""
            update orders set status = 'DELIVERED', delivered_at = ?, coupon_id = gen_random_uuid(),
                              coupon_code = 'BIENVENIDA', shipment_tracking_number = 'TN123456789AR'
            where id = ?
            """, Timestamp.from(NOW.plusSeconds(86_400)), order.id().value());

        ExportedOrder exported = asCustomer.all(ana).getFirst();

        assertThat(exported.deliveredAt()).contains(NOW.plusSeconds(86_400));
        assertThat(exported.couponCode()).contains("BIENVENIDA");
        assertThat(exported.shipmentTrackingNumber()).contains("TN123456789AR");
    }

    @Test
    void givenAnotherCustomersOrder_whenExportingItsOrdersAsThisCustomer_thenRowLevelSecurityHidesThem() {
        UserId ana = customer("ana@example.com");
        UserId bruno = customer("bruno@example.com");
        placed(bruno, NOW);

        List<ExportedOrder> asAna = asCustomer.as(ana, () -> asCustomer.adapter().all(bruno));

        assertThat(asAna).isEmpty();
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
    }

    /** Each call in its own committed transaction: inserts as the buyer, exports as the customer. */
    record AsCustomer(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, CustomerOrderExports {

        @Override
        public List<ExportedOrder> all(UserId customer) {
            return as(customer, () -> adapter.all(customer));
        }

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            Actor buyer = Actor.customer(order.userId().orElseThrow(), Optional.empty(), Optional.of(token));
            CurrentActor.runAs(buyer, () -> transactions.executeWithoutResult(status -> adapter.insert(order)));
        }

        @Override
        public long next() {
            Long number = CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.next()));
            return number == null ? 0 : number;
        }

        <R> R as(UserId customer, Supplier<R> work) {
            Actor actor = Actor.customer(customer, Optional.empty(), Optional.empty());
            return CurrentActor.callAs(actor, () -> {
                var readOnly = new TransactionTemplate(transactions.getTransactionManager());
                readOnly.setReadOnly(true);
                return readOnly.execute(status -> work.get());
            });
        }
    }
}
