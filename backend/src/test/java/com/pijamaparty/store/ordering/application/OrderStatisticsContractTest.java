package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.ordering.api.SalesFigures.DayTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.MonthTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.Total;
import com.pijamaparty.store.ordering.api.SalesFigures.VariantSales;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link SalesStatistics} and {@link WorkloadStatistics} (UC-33): only {@code PAID}, {@code PREPARING},
 * {@code SHIPPED} and {@code DELIVERED} orders count as sales, by Argentina date; the workload reads the received
 * withdrawals, the newest orders and the open ones. The buyer of every arranged order is Ana Pérez.
 */
public abstract class OrderStatisticsContractTest<T extends SalesStatistics & WorkloadStatistics>
    extends ContractTest<T> {

    /** 15 October 2026, 12:00 in Argentina. */
    protected static final Instant NOON = Instant.parse("2026-10-15T15:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-10-15T03:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-10-16T03:00:00Z");

    /** One line of an arranged order. */
    protected record Line(UUID variantId, int quantity, Money unitPrice) {}

    /** An order in {@code status} created at {@code createdAt}, of {@code total}, with its lines; answers its id. */
    protected abstract UUID givenOrder(OrderStatus status, Instant createdAt, Money total, List<Line> lines);

    /** A withdrawal request of {@code order}, still received or already resolved. */
    protected abstract void givenWithdrawal(UUID order, boolean received);

    private UUID order(OrderStatus status, Instant createdAt, long totalCents) {
        return givenOrder(status, createdAt, Money.of(totalCents), List.of());
    }

    @Test
    void givenOrdersInEveryStatus_whenTotalled_thenOnlyThoseThatCountAsSalesAddUp() {
        order(OrderStatus.PAID, NOON, 10_000_000);
        order(OrderStatus.PREPARING, NOON, 2_000_000);
        order(OrderStatus.SHIPPED, NOON, 3_000_000);
        order(OrderStatus.DELIVERED, NOON, 4_000_000);
        order(OrderStatus.AWAITING_PAYMENT, NOON, 50_000_000);
        order(OrderStatus.EXPIRED, NOON, 50_000_000);
        order(OrderStatus.CANCELLED, NOON, 50_000_000);
        persistAndForget();

        assertThat(subject.totals(DAY_START, DAY_END)).isEqualTo(new Total(Money.of(19_000_000), 4));
    }

    @Test
    void givenOrdersAtTheEdgesOfADay_whenTotalled_thenTheStartIsInAndTheEndIsOut() {
        order(OrderStatus.PAID, DAY_START, 1_000_000);
        order(OrderStatus.PAID, DAY_END.minusMillis(1), 2_000_000);
        order(OrderStatus.PAID, DAY_END, 4_000_000);
        persistAndForget();

        assertThat(subject.totals(DAY_START, DAY_END)).isEqualTo(new Total(Money.of(3_000_000), 2));
        assertThat(subject.totals(DAY_END, DAY_END.plusSeconds(86_400))).isEqualTo(new Total(Money.of(4_000_000), 1));
    }

    @Test
    void givenNoSales_whenTotalled_thenItIsZero() {
        assertThat(subject.totals(DAY_START, DAY_END)).isEqualTo(new Total(Money.zero(), 0));
    }

    @Test
    void givenAnOrderAt2330InArgentina_whenGroupedByDay_thenItBelongsToThatArgentinaDay() {
        order(OrderStatus.PAID, Instant.parse("2026-10-16T02:30:00Z"), 1_000_000);
        order(OrderStatus.PAID, NOON, 2_000_000);
        order(OrderStatus.CANCELLED, NOON, 9_000_000);
        persistAndForget();

        assertThat(subject.perDay(DAY_START.minusSeconds(86_400), DAY_END.plusSeconds(86_400)))
            .containsExactly(new DayTotal(LocalDate.parse("2026-10-15"), Money.of(3_000_000), 2));
    }

    @Test
    void givenSalesInTwoMonths_whenGroupedByMonth_thenEachMonthAddsItsOwnOldestFirst() {
        order(OrderStatus.DELIVERED, Instant.parse("2026-09-30T23:00:00Z"), 1_000_000);
        order(OrderStatus.PAID, Instant.parse("2026-10-01T04:00:00Z"), 2_000_000);
        order(OrderStatus.PAID, NOON, 3_000_000);
        persistAndForget();

        assertThat(subject.perMonth(Instant.parse("2026-09-01T03:00:00Z"), DAY_END)).containsExactly(
            new MonthTotal(YearMonth.parse("2026-09"), Money.of(1_000_000), 1),
            new MonthTotal(YearMonth.parse("2026-10"), Money.of(5_000_000), 2));
    }

    @Test
    void givenAVariantSoldInTwoOrders_whenGroupedByVariant_thenItsUnitsAndAmountAddUpAndUnsoldOnesDoNot() {
        UUID pink = UUID.randomUUID();
        UUID blue = UUID.randomUUID();
        givenOrder(OrderStatus.PAID, NOON, Money.of(9_000_000),
            List.of(new Line(pink, 3, Money.of(2_000_000)), new Line(blue, 1, Money.of(3_000_000))));
        givenOrder(OrderStatus.SHIPPED, NOON, Money.of(4_000_000), List.of(new Line(pink, 2, Money.of(2_000_000))));
        givenOrder(OrderStatus.AWAITING_PAYMENT, NOON, Money.of(4_000_000),
            List.of(new Line(blue, 5, Money.of(3_000_000))));
        persistAndForget();

        assertThat(subject.byVariant(DAY_START, DAY_END)).containsExactlyInAnyOrder(
            new VariantSales(pink, 5, Money.of(10_000_000)),
            new VariantSales(blue, 1, Money.of(3_000_000)));
    }

    @Test
    void givenReceivedAndResolvedWithdrawals_whenCounted_thenOnlyTheReceivedOnesWait() {
        givenWithdrawal(order(OrderStatus.PAID, NOON, 1_000_000), true);
        givenWithdrawal(order(OrderStatus.PAID, NOON, 1_000_000), true);
        givenWithdrawal(order(OrderStatus.CANCELLED, NOON, 1_000_000), false);
        persistAndForget();

        assertThat(subject.receivedWithdrawals()).isEqualTo(2);
    }

    @Test
    void givenSixOrders_whenTheNewestAreAsked_thenTheFiveNewestOfAnyStatusComeNewestFirst() {
        UUID oldest = order(OrderStatus.PAID, NOON, 1_000_000);
        for (int minutes = 1; minutes <= 4; minutes++) {
            order(OrderStatus.DELIVERED, NOON.plusSeconds(60L * minutes), 1_000_000);
        }
        UUID newest = order(OrderStatus.AWAITING_PAYMENT, NOON.plusSeconds(600), 2_500_000);
        persistAndForget();

        List<RecentOrder> recent = subject.newest(5);

        assertThat(recent).hasSize(5).extracting(RecentOrder::id).doesNotContain(oldest).startsWith(newest);
        assertThat(recent.getFirst()).extracting(RecentOrder::createdAt, RecentOrder::buyerName, RecentOrder::status,
            RecentOrder::total).containsExactly(NOON.plusSeconds(600), "Ana Pérez", Status.AWAITING_PAYMENT,
            Money.of(2_500_000));
    }

    @Test
    void givenOrdersInSeveralStatuses_whenAskedWhichAreOpen_thenPaidPreparingAndShippedAre() {
        UUID paid = order(OrderStatus.PAID, NOON, 1_000_000);
        UUID preparing = order(OrderStatus.PREPARING, NOON, 1_000_000);
        UUID shipped = order(OrderStatus.SHIPPED, NOON, 1_000_000);
        UUID delivered = order(OrderStatus.DELIVERED, NOON, 1_000_000);
        UUID cancelled = order(OrderStatus.CANCELLED, NOON, 1_000_000);
        persistAndForget();

        assertThat(subject.openAmong(List.of(paid, preparing, shipped, delivered, cancelled, UUID.randomUUID())))
            .containsExactlyInAnyOrder(paid, preparing, shipped);
        assertThat(subject.openAmong(List.of(delivered))).isEmpty();
    }
}
