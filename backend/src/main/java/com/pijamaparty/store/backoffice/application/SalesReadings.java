package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbound read port (UC-33): the orders that count as sales within an Argentina date range (both days included),
 * from the ordering module.
 */
public interface SalesReadings {

    SalesTotal total(DateRange range);

    /** Only the days with sales. */
    Map<LocalDate, SalesTotal> perDay(DateRange range);

    /** Only the months with sales. */
    Map<YearMonth, SalesTotal> perMonth(DateRange range);

    /** The units and amount sold of each variant. */
    List<VariantSold> byVariant(DateRange range);

    record VariantSold(UUID variantId, long units, Money amount) {

        public VariantSold {
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
