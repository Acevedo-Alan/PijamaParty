package com.pijamaparty.store.reviews.adapter.in.web;

import com.pijamaparty.store.reviews.application.ListApprovedReviews;
import com.pijamaparty.store.reviews.application.PublishedReview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-24 (T-E5-07): the approved reviews on the product page, public as the rest of the catalog. Only translates
 * between HTTP and the use case.
 */
@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catalog")
@ApiResponse(responseCode = "400", description = "Invalid parameters",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "429", description = "Over the catalog limit (RNF-22); Retry-After says when to retry",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class ProductReviewsController {

    /** Bounds the offset; far beyond the reviews one product gets. */
    private static final int MAX_PAGE = 1_000;

    /** A slug is only capped to bound the request, at 4 times the longest slug of the catalog, as its own routes. */
    private static final int MAX_SLUG = 560;

    private final ListApprovedReviews listApprovedReviews;

    ProductReviewsController(ListApprovedReviews listApprovedReviews) {
        this.listApprovedReviews = listApprovedReviews;
    }

    @GetMapping("/{slug}/reviews")
    @Operation(summary = "The approved reviews of a listed product, with their average and count",
        description = ListApprovedReviews.PAGE_SIZE + " per page, from page 0, newest first. average is the mean of "
            + "the approved ratings with one decimal, null without approved reviews; count is their number.")
    @ApiResponse(responseCode = "200", description = "One page of reviews", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ProductReviewsResponse.class)))
    @ApiResponse(responseCode = "404", description = "No listed product has this slug",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> listReviews(@PathVariable @Size(max = MAX_SLUG) String slug,
                                  @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (listApprovedReviews.execute(new ListApprovedReviews.Query(slug, page))) {
            case ListApprovedReviews.Result.Listed listed -> ResponseEntity.ok(new ProductReviewsResponse(
                listed.summary().average().orElse(null), listed.summary().count(),
                listed.reviews().stream().map(PublishedReviewResponse::of).toList()));
            case ListApprovedReviews.Result.ProductNotFound notFound -> {
                var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
                problem.setTitle("Product not found");
                yield ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
            }
        };
    }

    /**
     * @param average one decimal, null without approved reviews
     * @param count the number of approved reviews
     */
    record ProductReviewsResponse(@Schema(types = {"number", "null"}, example = "4.5") BigDecimal average,
                                  long count, List<PublishedReviewResponse> reviews) {}

    /**
     * @param authorName the author's first name
     * @param text null without one
     */
    record PublishedReviewResponse(String authorName, int rating, @Schema(nullable = true) String text,
                                   Instant createdAt) {

        static PublishedReviewResponse of(PublishedReview review) {
            return new PublishedReviewResponse(review.authorName().value(), review.content().rating(),
                review.content().text().orElse(null), review.createdAt());
        }

        @Override
        public String toString() {
            return "PublishedReviewResponse[rating=" + rating + ", ***]";
        }
    }
}
