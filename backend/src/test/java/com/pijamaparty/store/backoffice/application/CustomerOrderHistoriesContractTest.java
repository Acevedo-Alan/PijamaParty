package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderRow;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerOrderHistories} (UC-34, T-E8-11): the customer's linked orders, newest first, 20 per page;
 * every one counts as an order, only the paid ones neither cancelled nor expired as purchases; a guest's order with
 * the customer's email never counts.
 */
public abstract class CustomerOrderHistoriesContractTest<T extends CustomerOrderHistories> extends ContractTest<T> {

    protected static final Instant T0 = Instant.parse("2026-10-15T15:00:00Z");

    /** An order in {@code status} of {@code totalCents}, placed at {@code at}, linked to {@code customer} or a guest's. */
    protected abstract OrderRow givenOrder(Optional<UUID> customer, Status status, long totalCents, Instant at);

    @Test
    void givenADeliveredACancelledAndAnAwaitingOrder_whenRead_thenThreeOrdersOnePurchaseAnd30000Spent() {
        UUID ana = UUID.randomUUID();
        OrderRow delivered = givenOrder(Optional.of(ana), Status.DELIVERED, 3_000_000, T0);
        OrderRow cancelled = givenOrder(Optional.of(ana), Status.CANCELLED, 1_000_000, T0.plusSeconds(60));
        OrderRow awaiting = givenOrder(Optional.of(ana), Status.AWAITING_PAYMENT, 500_000, T0.plusSeconds(120));
        givenOrder(Optional.empty(), Status.PAID, 700_000, T0.plusSeconds(180));
        persistAndForget();

        assertThat(subject.historyOf(ana, 0)).isEqualTo(new OrderHistory(List.of(awaiting, cancelled, delivered),
            false, 3, 1, Money.of(3_000_000), Optional.of(T0)));
    }

    @Test
    void givenMoreOrdersThanAPage_whenRead_thenPagesOf20ComeWithWhetherMoreFollowAndTheFiguresOfAll() {
        UUID ana = UUID.randomUUID();
        IntStream.rangeClosed(1, CustomerOrders.PAGE_SIZE + 1)
            .forEach(i -> givenOrder(Optional.of(ana), Status.PAID, 100, T0.plusSeconds(i)));
        persistAndForget();

        OrderHistory first = subject.historyOf(ana, 0);
        OrderHistory second = subject.historyOf(ana, 1);

        assertThat(first.orders()).hasSize(CustomerOrders.PAGE_SIZE);
        assertThat(first.orders().getFirst().createdAt()).isEqualTo(T0.plusSeconds(CustomerOrders.PAGE_SIZE + 1));
        assertThat(first.hasMore()).isTrue();
        assertThat(second.orders()).extracting(OrderRow::createdAt).containsExactly(T0.plusSeconds(1));
        assertThat(second.hasMore()).isFalse();
        assertThat(second.orderCount()).isEqualTo(CustomerOrders.PAGE_SIZE + 1);
        assertThat(second.spent()).isEqualTo(Money.of(100L * (CustomerOrders.PAGE_SIZE + 1)));
    }

    @Test
    void givenACustomerWithoutOrders_whenRead_thenNothingAndZeros() {
        assertThat(subject.historyOf(UUID.randomUUID(), 0))
            .isEqualTo(new OrderHistory(List.of(), false, 0, 0, Money.zero(), Optional.empty()));
    }
}
