package com.pijamaparty.store.backoffice.adapter.in.web;

import com.pijamaparty.store.backoffice.application.GetDashboard;
import com.pijamaparty.store.backoffice.application.GetDashboard.Chart;
import com.pijamaparty.store.backoffice.application.GetDashboard.Dashboard;
import com.pijamaparty.store.backoffice.application.GetDashboard.PeriodSales;
import com.pijamaparty.store.backoffice.domain.BestSellers.Sold;
import com.pijamaparty.store.backoffice.domain.SalesSeries.Point;
import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-33: the back-office summary. Only translates between HTTP and the use case; amounts in cents. */
@RestController
@Tag(name = "Back office summary")
class DashboardAdminController {

    private final GetDashboard getDashboard;

    DashboardAdminController(GetDashboard getDashboard) {
        this.getDashboard = getDashboard;
    }

    @GetMapping("/api/admin/dashboard")
    @Operation(summary = "The summary: what to do today, sales, the chart, low stock, best sellers, recent orders "
        + "and the totals")
    @ApiResponse(responseCode = "200", description = "The summary", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = DashboardResponse.class)))
    @ApiResponse(responseCode = "400", description = "A chart other than DAYS or MONTHS",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the administrator",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> get(@Parameter(description = "The chart by the last 30 days or the last 12 months")
                          @RequestParam(defaultValue = "DAYS") Chart chart) {
        return switch (getDashboard.execute(new GetDashboard.Query(CurrentActor.get().orElseThrow(), chart))) {
            case GetDashboard.Result.Shown shown -> ResponseEntity.ok(DashboardResponse.of(shown.dashboard()));
            case GetDashboard.Result.Forbidden forbidden ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
        };
    }

    record DashboardResponse(TodoResponse todo, SalesResponse sales, ChartResponse chart,
                             LowStockResponse lowStock, List<BestSellerResponse> bestSellers,
                             List<RecentOrderResponse> recentOrders, TotalsResponse totals) {

        static DashboardResponse of(Dashboard dashboard) {
            var todo = dashboard.todo();
            var sales = dashboard.sales();
            var lowStock = dashboard.lowStock();
            return new DashboardResponse(
                new TodoResponse(todo.paidOrders(), todo.preparingOrders(), todo.receivedWithdrawals(),
                    todo.pendingReviews(), todo.emailAlerts(), todo.soldOutVariants()),
                new SalesResponse(PeriodResponse.of(sales.today()), PeriodResponse.of(sales.lastWeek()),
                    PeriodResponse.of(sales.month()), sales.averageTicket().map(Money::cents).orElse(null)),
                new ChartResponse(dashboard.chart(), dashboard.points().stream().map(PointResponse::of).toList()),
                new LowStockResponse(lowStock.threshold(), lowStock.count(),
                    lowStock.variants().stream().map(LowStockItemResponse::of).toList()),
                dashboard.bestSellers().stream().map(BestSellerResponse::of).toList(),
                dashboard.recentOrders().stream().map(RecentOrderResponse::of).toList(),
                new TotalsResponse(dashboard.totals().publishedProducts()));
        }
    }

    /** "Para hacer hoy": each count links to its list. */
    record TodoResponse(long paidOrders, long preparingOrders, long receivedWithdrawals, long pendingReviews,
                        long emailAlerts, long soldOutVariants) {}

    /** @param averageTicketCents this month's amount over its orders, rounded down; null without orders */
    record SalesResponse(PeriodResponse today, PeriodResponse lastWeek, PeriodResponse month,
                         @Schema(nullable = true) Long averageTicketCents) {}

    /** @param variation whole percents against the period before; null ("—") when nothing sold then */
    record PeriodResponse(long amountCents, long orders, @Schema(nullable = true) Integer variation) {

        static PeriodResponse of(PeriodSales period) {
            return new PeriodResponse(period.total().amount().cents(), period.total().orders(),
                period.variation().isPresent() ? period.variation().getAsInt() : null);
        }
    }

    /** @param points oldest first, a day (2026-10-31) or a month (2026-10) each, 0 when nothing sold */
    record ChartResponse(Chart kind, List<PointResponse> points) {}

    record PointResponse(String period, long amountCents, long orders) {

        static PointResponse of(Point point) {
            return new PointResponse(point.period(), point.amount().cents(), point.orders());
        }
    }

    /** @param count every variant at or under the threshold, of which at most 10 are listed */
    record LowStockResponse(int threshold, long count, List<LowStockItemResponse> items) {}

    record LowStockItemResponse(UUID variantId, UUID productId, String productName, String size, String color,
                                long available) {

        static LowStockItemResponse of(LowStockVariant variant) {
            return new LowStockItemResponse(variant.variantId(), variant.productId(), variant.productName(),
                variant.size(), variant.color(), variant.available());
        }
    }

    record BestSellerResponse(String productSlug, String productName, long units, long amountCents) {

        static BestSellerResponse of(Sold sold) {
            return new BestSellerResponse(sold.productSlug(), sold.productName(), sold.units(), sold.amount().cents());
        }
    }

    /** Personal data (R-05): the buyer's name; {@link #toString()} prints only the number. */
    record RecentOrderResponse(UUID id, long number, Instant createdAt, String buyerName, Status status,
                               long totalCents) {

        static RecentOrderResponse of(RecentOrder order) {
            return new RecentOrderResponse(order.id(), order.number(), order.createdAt(), order.buyerName(),
                order.status(), order.total().cents());
        }

        @Override
        public String toString() {
            return "RecentOrderResponse[number=" + number + ", ***]";
        }
    }

    record TotalsResponse(long publishedProducts) {}
}
