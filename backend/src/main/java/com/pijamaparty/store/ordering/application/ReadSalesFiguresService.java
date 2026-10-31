package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.SalesFigures;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-33: {@link SalesFigures} for the back-office summary, read as the caller (the administrator). */
@Service
class ReadSalesFiguresService implements SalesFigures {

    private final SalesStatistics statistics;

    ReadSalesFiguresService(SalesStatistics statistics) {
        this.statistics = statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public Total totals(Instant from, Instant to) {
        return statistics.totals(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DayTotal> perDay(Instant from, Instant to) {
        return statistics.perDay(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthTotal> perMonth(Instant from, Instant to) {
        return statistics.perMonth(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VariantSales> byVariant(Instant from, Instant to) {
        return statistics.byVariant(from, to);
    }
}
