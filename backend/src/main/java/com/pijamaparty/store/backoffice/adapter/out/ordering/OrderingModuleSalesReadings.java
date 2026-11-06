package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.SalesReadings;
import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.ordering.api.SalesFigures;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reads the sales through the public API of the ordering module, in the caller's transaction: a store date range
 * (the zone of the injected clock, R-07) becomes the instants from its first day's midnight, included, to the midnight after its last day, excluded.
 */
@Component
class OrderingModuleSalesReadings implements SalesReadings {

    private final SalesFigures figures;
    private final ZoneId zone;

    OrderingModuleSalesReadings(SalesFigures figures, Clock clock) {
        this.figures = figures;
        this.zone = clock.getZone();
    }

    @Override
    public SalesTotal total(DateRange range) {
        SalesFigures.Total total = figures.totals(range.start(zone), range.end(zone));
        return new SalesTotal(total.amount(), total.orders());
    }

    @Override
    public Map<LocalDate, SalesTotal> perDay(DateRange range) {
        return figures.perDay(range.start(zone), range.end(zone)).stream().collect(Collectors.toUnmodifiableMap(
            SalesFigures.DayTotal::day, day -> new SalesTotal(day.amount(), day.orders())));
    }

    @Override
    public Map<YearMonth, SalesTotal> perMonth(DateRange range) {
        return figures.perMonth(range.start(zone), range.end(zone)).stream().collect(Collectors.toUnmodifiableMap(
            SalesFigures.MonthTotal::month, month -> new SalesTotal(month.amount(), month.orders())));
    }

    @Override
    public List<VariantSold> byVariant(DateRange range) {
        return figures.byVariant(range.start(zone), range.end(zone)).stream()
            .map(sold -> new VariantSold(sold.variantId(), sold.units(), sold.amount()))
            .toList();
    }
}
