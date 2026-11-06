package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.application.CatalogFigures.ProductOf;
import com.pijamaparty.store.backoffice.application.SalesReadings.VariantSold;
import com.pijamaparty.store.backoffice.domain.BestSellers;
import com.pijamaparty.store.backoffice.domain.BestSellers.Sold;
import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesPeriods;
import com.pijamaparty.store.backoffice.domain.SalesPeriods.Compared;
import com.pijamaparty.store.backoffice.domain.SalesSeries;
import com.pijamaparty.store.backoffice.domain.SalesSeries.Point;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.backoffice.domain.Variation;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetDashboardService implements GetDashboard {

    static final int LOW_STOCK_SHOWN = 10;
    static final int BEST_SELLERS_SHOWN = 5;
    static final int RECENT_ORDERS_SHOWN = 5;

    private final OrderCounts orders;
    private final SalesReadings sales;
    private final BuyerEmails buyerEmails;
    private final CatalogFigures catalog;
    private final PendingReviewCount reviews;
    private final LowStockThreshold threshold;
    private final CustomerFigures customers;
    private final Clock clock;

    GetDashboardService(OrderCounts orders, SalesReadings sales, BuyerEmails buyerEmails, CatalogFigures catalog,
                        PendingReviewCount reviews, LowStockThreshold threshold, CustomerFigures customers,
                        Clock clock) {
        this.customers = customers;
        this.orders = orders;
        this.sales = sales;
        this.buyerEmails = buyerEmails;
        this.catalog = catalog;
        this.reviews = reviews;
        this.threshold = threshold;
        this.clock = clock;
    }

    /** Every block in one read-only transaction, as the administrator. Store dates come from the clock's zone (R-07). */
    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        SalesPeriods periods = SalesPeriods.of(LocalDate.now(clock));
        Optional<Compared> chosen = chosen(query, periods);
        if (chosen.isEmpty()) {
            return new Result.InvalidPeriod();
        }
        int units = threshold.units();
        var todo = new Todo(orders.paidWaiting(), orders.preparing(), orders.receivedWithdrawals(), reviews.pending(),
            orders.openAmong(buyerEmails.ordersWithProblems()).size(), catalog.lowStockCount(0));
        DateRange period = chosen.get().period();
        SalesTotal total = sales.total(period);
        var salesBlock = new Sales(query.period(), period, period(total, chosen.get().before()), total.averageTicket());
        var lowStock = new LowStock(units, catalog.lowStock(units, LOW_STOCK_SHOWN), catalog.lowStockCount(units));
        return new Result.Shown(new Dashboard(todo, salesBlock, query.chart(), chart(query.chart(), periods), lowStock,
            bestSellers(periods.chartDays()), orders.newest(RECENT_ORDERS_SHOWN),
            totals(periods)));
    }

    /** UC-33: the customers counted from the first of this month, a store date (R-07). */
    private Totals totals(SalesPeriods periods) {
        var counts = customers.count(periods.month().start(clock.getZone()));
        return new Totals(catalog.publishedProducts(), counts.registered(), counts.registeredSince());
    }

    /** The chosen period and the one it is compared against; empty when the query's period is not valid. */
    private static Optional<Compared> chosen(Query query, SalesPeriods periods) {
        if (query.period() == Period.CUSTOM) {
            return query.from().isEmpty() || query.to().isEmpty()
                ? Optional.empty()
                : periods.custom(query.from().get(), query.to().get())
                    .map(range -> new Compared(range, SalesPeriods.before(range)));
        }
        if (query.from().isPresent() || query.to().isPresent()) {
            return Optional.empty();
        }
        return Optional.of(switch (query.period()) {
            case TODAY -> new Compared(periods.today(), periods.yesterday());
            case LAST_7_DAYS -> new Compared(periods.lastWeek(), periods.weekBefore());
            // CUSTOM returned above; the switch lists it to stay exhaustive
            case MONTH, CUSTOM -> new Compared(periods.month(), periods.monthBefore());
        });
    }

    private PeriodSales period(SalesTotal total, DateRange before) {
        return new PeriodSales(total, Variation.percent(total.amount(), sales.total(before).amount()));
    }

    private List<Point> chart(Chart chart, SalesPeriods periods) {
        return switch (chart) {
            case DAYS -> SalesSeries.days(periods.chartDays(), sales.perDay(periods.chartDays()));
            case MONTHS -> SalesSeries.months(periods.chartMonths(), sales.perMonth(periods.chartMonths()));
        };
    }

    /** The last 30 days, the same days as the chart by days. */
    private List<Sold> bestSellers(DateRange days) {
        List<VariantSold> sold = sales.byVariant(days);
        Map<UUID, ProductOf> products = catalog.productsOf(sold.stream().map(VariantSold::variantId).toList());
        List<Sold> perVariant = sold.stream()
            .filter(variant -> products.containsKey(variant.variantId()))
            .map(variant -> {
                ProductOf product = products.get(variant.variantId());
                return new Sold(product.slug(), product.name(), variant.units(), variant.amount());
            })
            .toList();
        return BestSellers.top(perVariant, BEST_SELLERS_SHOWN);
    }
}
