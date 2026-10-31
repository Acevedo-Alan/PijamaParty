package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.application.CatalogFigures.ProductOf;
import com.pijamaparty.store.backoffice.application.SalesReadings.VariantSold;
import com.pijamaparty.store.backoffice.domain.BestSellers;
import com.pijamaparty.store.backoffice.domain.BestSellers.Sold;
import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesPeriods;
import com.pijamaparty.store.backoffice.domain.SalesSeries;
import com.pijamaparty.store.backoffice.domain.SalesSeries.Point;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.backoffice.domain.Variation;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final Clock clock;

    GetDashboardService(OrderCounts orders, SalesReadings sales, BuyerEmails buyerEmails, CatalogFigures catalog,
                        PendingReviewCount reviews, LowStockThreshold threshold, Clock clock) {
        this.orders = orders;
        this.sales = sales;
        this.buyerEmails = buyerEmails;
        this.catalog = catalog;
        this.reviews = reviews;
        this.threshold = threshold;
        this.clock = clock;
    }

    /** Every block in one read-only transaction, as the administrator. */
    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        SalesPeriods periods = SalesPeriods.of(LocalDate.now(clock.withZone(SalesPeriods.ARGENTINA)));
        int units = threshold.units();
        var todo = new Todo(orders.paidWaiting(), orders.preparing(), orders.receivedWithdrawals(), reviews.pending(),
            orders.openAmong(buyerEmails.ordersWithProblems()).size(), catalog.lowStockCount(0));
        SalesTotal month = sales.total(periods.month());
        var salesBlock = new Sales(period(periods.today(), periods.yesterday()),
            period(periods.lastWeek(), periods.weekBefore()), period(month, periods.monthBefore()),
            month.averageTicket());
        var lowStock = new LowStock(units, catalog.lowStock(units, LOW_STOCK_SHOWN), catalog.lowStockCount(units));
        return new Result.Shown(new Dashboard(todo, salesBlock, query.chart(), chart(query.chart(), periods), lowStock,
            bestSellers(periods.chartDays()), orders.newest(RECENT_ORDERS_SHOWN),
            new Totals(catalog.publishedProducts())));
    }

    private PeriodSales period(DateRange range, DateRange before) {
        return period(sales.total(range), before);
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
