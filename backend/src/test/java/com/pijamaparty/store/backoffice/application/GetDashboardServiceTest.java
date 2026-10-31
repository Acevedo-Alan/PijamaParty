package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.application.GetDashboard.Chart;
import com.pijamaparty.store.backoffice.application.GetDashboard.Dashboard;
import com.pijamaparty.store.backoffice.application.GetDashboard.Query;
import com.pijamaparty.store.backoffice.application.GetDashboard.Result;
import com.pijamaparty.store.backoffice.domain.BestSellers.Sold;
import com.pijamaparty.store.backoffice.domain.SalesSeries.Point;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetDashboardServiceTest {

    /** 15 October 2026, 12:00 in Argentina. */
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryOrderCounts orders = new InMemoryOrderCounts();
    private final InMemorySalesReadings sales = new InMemorySalesReadings();
    private final InMemoryBuyerEmails buyerEmails = new InMemoryBuyerEmails();
    private final InMemoryCatalogFigures catalog = new InMemoryCatalogFigures();
    private final InMemoryPendingReviewCount reviews = new InMemoryPendingReviewCount();
    private final GetDashboardService service = new GetDashboardService(orders, sales, buyerEmails, catalog, reviews,
        new InMemoryLowStockThreshold(3), Clock.fixed(NOW, ZoneOffset.UTC));

    private Dashboard shown(Chart chart) {
        Result result = service.execute(new Query(ADMIN, chart));
        assertThat(result).isInstanceOf(Result.Shown.class);
        return ((Result.Shown) result).dashboard();
    }

    private void sold(String at, long cents) {
        sales.sold(Instant.parse(at), Money.of(cents), UUID.randomUUID(), 1, Money.of(cents));
    }

    @Test
    void givenACustomer_whenTheSummaryIsAsked_thenItIsForbidden() {
        Actor customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(service.execute(new Query(customer, Chart.DAYS))).isEqualTo(new Result.Forbidden());
    }

    @Test
    void given100000YesterdayAnd150000Today_whenTheSummaryOpens_thenTodayShowsPlus50Percent() {
        sold("2026-10-14T15:00:00Z", 10_000_000);
        sold("2026-10-15T14:00:00Z", 15_000_000);

        var today = shown(Chart.DAYS).sales().today();

        assertThat(today.total()).isEqualTo(new SalesTotal(Money.of(15_000_000), 1));
        assertThat(today.variation()).hasValue(50);
    }

    @Test
    void givenNothingSoldYesterday_whenTheSummaryOpens_thenTodaysVariationIsEmpty() {
        sold("2026-10-15T14:00:00Z", 15_000_000);

        assertThat(shown(Chart.DAYS).sales().today().variation()).isEmpty();
    }

    @Test
    void givenAnOrderAt2330InArgentinaYesterday_whenTheSummaryOpens_thenItIsYesterdaysNotTodays() {
        sold("2026-10-15T02:30:00Z", 1_000_000);

        var salesBlock = shown(Chart.DAYS).sales();

        assertThat(salesBlock.today().total()).isEqualTo(SalesTotal.NONE);
        assertThat(salesBlock.lastWeek().total()).isEqualTo(new SalesTotal(Money.of(1_000_000), 1));
    }

    @Test
    void givenThreeOrdersThisMonth_whenTheSummaryOpens_thenTheAverageTicketIsRoundedDown() {
        sold("2026-10-01T15:00:00Z", 1_000_000);
        sold("2026-10-02T15:00:00Z", 0);
        sold("2026-10-03T15:00:00Z", 0);

        assertThat(shown(Chart.DAYS).sales().averageTicket()).contains(Money.of(333_333));
    }

    @Test
    void givenADayWithoutSales_whenTheChartShowsDays_thenThereAre30BarsAndThatDayIsZero() {
        sold("2026-10-15T14:00:00Z", 1_000_000);

        Dashboard dashboard = shown(Chart.DAYS);

        assertThat(dashboard.chart()).isEqualTo(Chart.DAYS);
        assertThat(dashboard.points()).hasSize(30).last()
            .isEqualTo(new Point("2026-10-15", Money.of(1_000_000), 1));
        assertThat(dashboard.points().get(28)).isEqualTo(new Point("2026-10-14", Money.zero(), 0));
    }

    @Test
    void givenTheChartByMonths_whenTheSummaryOpens_thenThereAre12BarsEndingThisMonth() {
        Dashboard dashboard = shown(Chart.MONTHS);

        assertThat(dashboard.points()).hasSize(12).extracting(Point::period).startsWith("2025-11").endsWith("2026-10");
    }

    @Test
    void givenWorkInEveryList_whenTheSummaryOpens_thenParaHacerHoyCountsIt() {
        orders.paid();
        orders.paid();
        orders.order(Status.PREPARING, NOW);
        orders.withdrawalReceived();
        reviews.submitted();
        UUID shipped = orders.order(Status.SHIPPED, NOW);
        UUID delivered = orders.order(Status.DELIVERED, NOW);
        buyerEmails.bounced(shipped);
        buyerEmails.bounced(delivered);
        catalog.variant(catalog.product("Luna", true), "M", 0, true, 0);

        var todo = shown(Chart.DAYS).todo();

        assertThat(todo).isEqualTo(new GetDashboard.Todo(2, 1, 1, 1, 1, 1));
    }

    @Test
    void given12VariantsAtOrUnderTheThreshold_whenTheSummaryOpens_then10ShowAndTheCountIs12() {
        UUID luna = catalog.product("Luna", true);
        for (int size = 0; size < 12; size++) {
            catalog.variant(luna, "T" + size, size, true, 3);
        }
        catalog.variant(luna, "XL", 12, true, 4);

        var lowStock = shown(Chart.DAYS).lowStock();

        assertThat(lowStock.threshold()).isEqualTo(3);
        assertThat(lowStock.variants()).hasSize(10);
        assertThat(lowStock.count()).isEqualTo(12);
    }

    @Test
    void givenAProductSoldInTwoSizes_whenTheSummaryOpens_thenTheBestSellersShowItOnceWith5Units() {
        UUID luna = catalog.product("Luna", true);
        UUID small = catalog.variant(luna, "S", 0, true, 10);
        UUID medium = catalog.variant(luna, "M", 1, true, 10);
        sales.sold(NOW.minusSeconds(3600), Money.of(6_000_000), small, 3, Money.of(6_000_000));
        sales.sold(NOW.minusSeconds(7200), Money.of(4_000_000), medium, 2, Money.of(4_000_000));

        assertThat(shown(Chart.DAYS).bestSellers())
            .containsExactly(new Sold("luna", "Luna", 5, Money.of(10_000_000)));
    }

    @Test
    void givenSixOrders_whenTheSummaryOpens_thenTheFiveNewestShowAndThePublishedProductsAreCounted() {
        for (int minutes = 0; minutes < 6; minutes++) {
            orders.order(Status.PAID, NOW.minusSeconds(60L * minutes));
        }
        catalog.product("Luna", true);
        catalog.product("Oculto", false);

        Dashboard dashboard = shown(Chart.DAYS);

        assertThat(dashboard.recentOrders()).hasSize(5);
        assertThat(dashboard.recentOrders().getFirst().createdAt()).isEqualTo(NOW);
        assertThat(dashboard.totals().publishedProducts()).isEqualTo(1);
    }
}
