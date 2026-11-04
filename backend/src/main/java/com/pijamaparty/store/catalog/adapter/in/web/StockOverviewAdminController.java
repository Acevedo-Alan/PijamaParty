package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.ListStockOverview;
import com.pijamaparty.store.catalog.application.StockOverviewRow;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.catalog.domain.StockFilter;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-07 (T-E8-09): the "Stock" screen of the back office. Only translates between HTTP and the use case. */
@RestController
@RequestMapping("/api/admin/catalog/stock")
@Tag(name = "Back office stock")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class StockOverviewAdminController {

    /** Bounds the offset; 10,000 pages of 50 are far beyond the catalog of the shop. */
    private static final int MAX_PAGE = 10_000;

    private final ListStockOverview list;

    StockOverviewAdminController(ListStockOverview list) {
        this.list = list;
    }

    @GetMapping
    @Operation(summary = "The active variants of every product with their stock, fewest available first",
        description = "50 per page, from page 0, then by product name, size and color in display order. `filter` is "
            + "ALL (the default), LOW (available above 0 and at or under the low-stock threshold of the store's "
            + "settings) or OUT (none available); available is on hand minus reserved. `q` is 2 to 100 characters "
            + "that the product's name contains, ignoring case and accents, every character literal, or the "
            + "variant's whole SKU, ignoring case. Adjust with POST /api/admin/stock-adjustments.")
    @ApiResponse(responseCode = "200", description = "One page of variants", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = StockPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown filter, a category that is not a UUID, page out of "
        + "range, or INVALID_SEARCH (1 character or over 100)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam(defaultValue = "ALL") StockFilter filter,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) UUID category,
                           @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        var query = new ListStockOverview.Query(actor(), filter, Optional.ofNullable(q),
            Optional.ofNullable(category).map(CategoryId::of), page);
        return switch (list.execute(query)) {
            case ListStockOverview.Result.Listed listed -> ResponseEntity.ok(new StockPageResponse(
                listed.rows().stream().map(StockRowResponse::of).toList(), listed.hasMore()));
            case ListStockOverview.Result.InvalidSearch invalid -> invalidSearch();
            case ListStockOverview.Result.Forbidden forbidden ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidSearch() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "2 to " + ProductSearch.MAX_LENGTH
            + " characters of the product's name, or a SKU.");
        problem.setTitle("Invalid search");
        problem.setProperty("problem", "INVALID_SEARCH");
        return ResponseEntity.badRequest().body(problem);
    }

    record StockPageResponse(List<StockRowResponse> items, boolean hasMore) {}

    record StockRowResponse(UUID variantId, UUID productId, String productName, String size, String color, String sku,
                            boolean published, int onHand, int reserved, int available) {

        static StockRowResponse of(StockOverviewRow row) {
            return new StockRowResponse(row.variantId().value(), row.productId().value(), row.productName(),
                row.size(), row.color(), row.sku(), row.published(), row.onHand(), row.reserved(), row.available());
        }
    }
}
