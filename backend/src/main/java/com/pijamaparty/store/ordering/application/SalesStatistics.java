package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.SalesFigures.DayTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.MonthTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.Total;
import com.pijamaparty.store.ordering.api.SalesFigures.VariantSales;
import java.time.Instant;
import java.util.List;

/**
 * Outbound read port (UC-33): the orders that count as sales ({@code OrderStatus.isBought()}) created from
 * {@code from}, included, to {@code to}, excluded; grouped by Argentina date. Within the caller's transaction.
 */
public interface SalesStatistics {

    Total totals(Instant from, Instant to);

    List<DayTotal> perDay(Instant from, Instant to);

    List<MonthTotal> perMonth(Instant from, Instant to);

    List<VariantSales> byVariant(Instant from, Instant to);
}
