package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.ordering.api.SalesFigures.DayTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.MonthTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.Total;
import com.pijamaparty.store.ordering.api.SalesFigures.VariantSales;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Fake of {@link SalesStatistics} and {@link WorkloadStatistics}: orders and withdrawal requests as arranged. */
public class InMemoryOrderStatistics implements SalesStatistics, WorkloadStatistics {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    /** One line of an arranged order. */
    public record Line(UUID variantId, int quantity, Money unitPrice) {}

    private record Stored(UUID id, long number, OrderStatus status, Instant createdAt, Money total, List<Line> lines) {}

    private final List<Stored> orders = new ArrayList<>();
    private long received;
    private long lastNumber = 1000;

    /** Test arrangement: an order of Ana Pérez; answers its id. */
    public synchronized UUID order(OrderStatus status, Instant createdAt, Money total, List<Line> lines) {
        UUID id = UUID.randomUUID();
        orders.add(new Stored(id, ++lastNumber, status, createdAt, total, List.copyOf(lines)));
        return id;
    }

    /** Test arrangement: one more withdrawal request still received. */
    public synchronized void withdrawalReceived() {
        received++;
    }

    private List<Stored> sales(Instant from, Instant to) {
        return orders.stream()
            .filter(order -> order.status().isBought())
            .filter(order -> !order.createdAt().isBefore(from) && order.createdAt().isBefore(to))
            .toList();
    }

    private static <K extends Comparable<? super K>> Map<K, Total> grouped(List<Stored> sold, Function<Stored, K> key) {
        Map<K, Total> totals = new LinkedHashMap<>();
        sold.stream().sorted(Comparator.comparing(key)).forEach(order -> totals.merge(key.apply(order),
            new Total(order.total(), 1),
            (a, b) -> new Total(a.amount().plus(b.amount()), a.orders() + b.orders())));
        return totals;
    }

    private static LocalDate day(Stored order) {
        return order.createdAt().atZone(ARGENTINA).toLocalDate();
    }

    @Override
    public synchronized Total totals(Instant from, Instant to) {
        List<Stored> sold = sales(from, to);
        return new Total(sold.stream().map(Stored::total).reduce(Money.zero(), Money::plus), sold.size());
    }

    @Override
    public synchronized List<DayTotal> perDay(Instant from, Instant to) {
        return grouped(sales(from, to), InMemoryOrderStatistics::day).entrySet().stream()
            .map(entry -> new DayTotal(entry.getKey(), entry.getValue().amount(), entry.getValue().orders()))
            .toList();
    }

    @Override
    public synchronized List<MonthTotal> perMonth(Instant from, Instant to) {
        return grouped(sales(from, to), order -> YearMonth.from(day(order))).entrySet().stream()
            .map(entry -> new MonthTotal(entry.getKey(), entry.getValue().amount(), entry.getValue().orders()))
            .toList();
    }

    @Override
    public synchronized List<VariantSales> byVariant(Instant from, Instant to) {
        Map<UUID, VariantSales> sold = new LinkedHashMap<>();
        sales(from, to).stream().flatMap(order -> order.lines().stream()).forEach(line -> sold.merge(line.variantId(),
            new VariantSales(line.variantId(), line.quantity(), line.unitPrice().times(line.quantity())),
            (a, b) -> new VariantSales(a.variantId(), a.units() + b.units(), a.amount().plus(b.amount()))));
        return List.copyOf(sold.values());
    }

    @Override
    public synchronized long receivedWithdrawals() {
        return received;
    }

    @Override
    public synchronized List<RecentOrder> newest(int limit) {
        return orders.stream()
            .sorted(Comparator.comparing(Stored::createdAt).thenComparing(Stored::number).reversed())
            .limit(limit)
            .map(order -> new RecentOrder(order.id(), order.number(), order.createdAt(), "Ana Pérez",
                Status.valueOf(order.status().name()), order.total()))
            .toList();
    }

    @Override
    public synchronized Set<UUID> openAmong(Collection<UUID> orderIds) {
        return orders.stream()
            .filter(order -> orderIds.contains(order.id()) && order.status().isOpen())
            .map(Stored::id)
            .collect(Collectors.toUnmodifiableSet());
    }
}
