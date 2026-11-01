package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.BestSellers.Sold;
import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesSeries.Point;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * UC-33: the summary of the business the administrator lands on: what to do today, the sales of the chosen period
 * against the period before, the chart, low stock, best sellers, recent orders and the totals.
 */
public interface GetDashboard {

    Result execute(Query query);

    /** The chart by days (the last 30) or by months (the last 12). */
    enum Chart {
        DAYS,
        MONTHS
    }

    /** The sales block's period (T-E8-20): today, the last 7 days, this month, or the dates given. */
    enum Period {
        TODAY,
        LAST_7_DAYS,
        MONTH,
        CUSTOM
    }

    /** {@code from} and {@code to}, store dates, both included, come with {@code CUSTOM} only. */
    record Query(Actor actor, Chart chart, Period period, Optional<LocalDate> from, Optional<LocalDate> to) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(chart, "chart");
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }

        /** This month's sales. */
        public Query(Actor actor, Chart chart) {
            this(actor, chart, Period.MONTH, Optional.empty(), Optional.empty());
        }
    }

    sealed interface Result {

        record Shown(Dashboard dashboard) implements Result {}

        /**
         * A custom period without both dates, dates with another period, {@code from} after {@code to}, {@code to}
         * after today, or more than 366 days.
         */
        record InvalidPeriod() implements Result {}

        record Forbidden() implements Result {}
    }

    record Dashboard(Todo todo, Sales sales, Chart chart, List<Point> points, LowStock lowStock,
                     List<Sold> bestSellers, List<RecentOrder> recentOrders, Totals totals) {

        public Dashboard {
            Objects.requireNonNull(todo, "todo");
            Objects.requireNonNull(sales, "sales");
            Objects.requireNonNull(chart, "chart");
            points = List.copyOf(points);
            Objects.requireNonNull(lowStock, "lowStock");
            bestSellers = List.copyOf(bestSellers);
            recentOrders = List.copyOf(recentOrders);
            Objects.requireNonNull(totals, "totals");
        }
    }

    /** "Para hacer hoy": each count links to its list; a 0 still shows. */
    record Todo(long paidOrders, long preparingOrders, long receivedWithdrawals, long pendingReviews,
                long emailAlerts, long soldOutVariants) {}

    /** A period's sales and its variation against the period before, in whole percents (empty: "—"). */
    record PeriodSales(SalesTotal total, OptionalInt variation) {

        public PeriodSales {
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(variation, "variation");
        }
    }

    /** The chosen period, its dates, its sales and its average ticket (empty without orders: "—"). */
    record Sales(Period period, DateRange range, PeriodSales sales, Optional<Money> averageTicket) {

        public Sales {
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(sales, "sales");
            Objects.requireNonNull(averageTicket, "averageTicket");
        }
    }

    /** At most 10 variants, with the number of all of them. */
    record LowStock(int threshold, List<LowStockVariant> variants, long count) {

        public LowStock {
            variants = List.copyOf(variants);
        }
    }

    /** The published products; the registered customers join with T-E8-10. */
    record Totals(long publishedProducts) {}
}
