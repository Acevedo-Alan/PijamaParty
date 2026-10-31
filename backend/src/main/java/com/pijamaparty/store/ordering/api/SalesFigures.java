package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-33: the sales of the back-office summary. An order counts as a sale when it is {@code PAID}, {@code PREPARING},
 * {@code SHIPPED} or {@code DELIVERED}, by its {@code total_cents} and the Argentina date of its {@code created_at}.
 * Periods run from {@code from}, included, to {@code to}, excluded. Read in its own transaction, or the caller's, as
 * the caller: the administrator. No personal data.
 */
public interface SalesFigures {

    Total totals(Instant from, Instant to);

    /** Only the days with sales, oldest first. */
    List<DayTotal> perDay(Instant from, Instant to);

    /** Only the months with sales, oldest first. */
    List<MonthTotal> perMonth(Instant from, Instant to);

    /** The units and the amount (unit price × quantity) of each variant sold. */
    List<VariantSales> byVariant(Instant from, Instant to);

    record Total(Money amount, long orders) {

        public Total {
            Objects.requireNonNull(amount, "amount");
        }
    }

    record DayTotal(LocalDate day, Money amount, long orders) {

        public DayTotal {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(amount, "amount");
        }
    }

    record MonthTotal(YearMonth month, Money amount, long orders) {

        public MonthTotal {
            Objects.requireNonNull(month, "month");
            Objects.requireNonNull(amount, "amount");
        }
    }

    record VariantSales(UUID variantId, long units, Money amount) {

        public VariantSales {
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
