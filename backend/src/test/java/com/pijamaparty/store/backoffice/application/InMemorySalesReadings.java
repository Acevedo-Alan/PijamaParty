package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesPeriods;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Fake of {@link SalesReadings}: the sales the test arranges, each of one variant. */
public class InMemorySalesReadings implements SalesReadings {

    private record Sale(Instant createdAt, Money total, UUID variant, long units, Money lineAmount) {}

    private final List<Sale> sales = new ArrayList<>();

    /** Test arrangement: an order that counts as a sale. */
    public synchronized void sold(Instant createdAt, Money total, UUID variant, long units, Money lineAmount) {
        sales.add(new Sale(createdAt, total, variant, units, lineAmount));
    }

    private List<Sale> within(DateRange range) {
        return sales.stream()
            .filter(sale -> !sale.createdAt().isBefore(range.start()) && sale.createdAt().isBefore(range.end()))
            .toList();
    }

    private static LocalDate day(Sale sale) {
        return sale.createdAt().atZone(SalesPeriods.ARGENTINA).toLocalDate();
    }

    private <K> Map<K, SalesTotal> grouped(DateRange range, Function<Sale, K> key) {
        Map<K, SalesTotal> totals = new HashMap<>();
        within(range).forEach(sale -> totals.merge(key.apply(sale), new SalesTotal(sale.total(), 1),
            (a, b) -> new SalesTotal(a.amount().plus(b.amount()), a.orders() + b.orders())));
        return Map.copyOf(totals);
    }

    @Override
    public synchronized SalesTotal total(DateRange range) {
        List<Sale> sold = within(range);
        return new SalesTotal(sold.stream().map(Sale::total).reduce(Money.zero(), Money::plus), sold.size());
    }

    @Override
    public synchronized Map<LocalDate, SalesTotal> perDay(DateRange range) {
        return grouped(range, InMemorySalesReadings::day);
    }

    @Override
    public synchronized Map<YearMonth, SalesTotal> perMonth(DateRange range) {
        return grouped(range, sale -> YearMonth.from(day(sale)));
    }

    @Override
    public synchronized List<VariantSold> byVariant(DateRange range) {
        Map<UUID, VariantSold> sold = new LinkedHashMap<>();
        within(range).forEach(sale -> sold.merge(sale.variant(),
            new VariantSold(sale.variant(), sale.units(), sale.lineAmount()),
            (a, b) -> new VariantSold(a.variantId(), a.units() + b.units(), a.amount().plus(b.amount()))));
        return List.copyOf(sold.values());
    }
}
