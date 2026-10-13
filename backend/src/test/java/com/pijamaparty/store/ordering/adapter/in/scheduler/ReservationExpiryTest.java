package com.pijamaparty.store.ordering.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** UC-16 end to end: the scheduled expiry as {@code SYSTEM}, over the real orders, stock and policies. */
@ApiTest
class ReservationExpiryTest {

    private final ReservationExpiry job;
    private final StockReservations reservations;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    ReservationExpiryTest(ReservationExpiry job, StockReservations reservations,
                          PlatformTransactionManager transactionManager, PostgreSQLContainer<?> postgres) {
        this.job = job;
        this.reservations = reservations;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate order_tracking_token, order_item, orders, stock_movement, stock_level");
    }

    @Test
    void givenAnExpiredReservation_whenTheCleanupRuns_thenTheStockIsAvailableAgainAndTheOrderIsExpired() {
        UUID variant = stocked(1);
        OrderId expired = order(OrderStatus.AWAITING_PAYMENT, Instant.now().minusSeconds(60), variant, 1);

        job.run();

        assertThat(OrderRows.statusOf(owner, expired)).contains(OrderStatus.EXPIRED);
        assertThat(available(variant)).isEqualTo(1);
        assertThat(owner.queryForList("select type from stock_movement where order_id = ? order by created_at",
            String.class, expired.value())).containsExactly("RESERVATION", "RELEASE");
    }

    @Test
    void givenReservationsStillRunning_whenTheCleanupRuns_thenTheyKeepTheirStock() {
        UUID variant = stocked(3);
        OrderId expired = order(OrderStatus.AWAITING_PAYMENT, Instant.now().minusSeconds(60), variant, 1);
        OrderId running = order(OrderStatus.AWAITING_PAYMENT, Instant.now().plusSeconds(1500), variant, 2);

        job.run();
        job.run();

        assertThat(OrderRows.statusOf(owner, expired)).contains(OrderStatus.EXPIRED);
        assertThat(OrderRows.statusOf(owner, running)).contains(OrderStatus.AWAITING_PAYMENT);
        assertThat(available(variant)).isEqualTo(1);
    }

    private UUID stocked(int onHand) {
        UUID variant = UUID.randomUUID();
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variant,
            onHand);
        return variant;
    }

    /** An order as checkout leaves it: stored and holding its reservation (T-E3-05). */
    private OrderId order(OrderStatus status, Instant reservationExpiresAt, UUID variant, int units) {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, status, reservationExpiresAt);
        var result = transactions.execute(transaction -> reservations.reserve(order.value(),
            List.of(new StockReservations.StockRequest(variant, units))));
        assertThat(result).isEqualTo(new StockReservations.ReservationResult.Reserved());
        return order;
    }

    private int available(UUID variant) {
        Integer available = owner.queryForObject("select available from v_variant_availability where variant_id = ?",
            Integer.class, variant);
        return available == null ? 0 : available;
    }
}
