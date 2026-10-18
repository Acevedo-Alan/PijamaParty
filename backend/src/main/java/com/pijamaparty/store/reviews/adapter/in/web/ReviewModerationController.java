package com.pijamaparty.store.reviews.adapter.in.web;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.reviews.application.ApproveReview;
import com.pijamaparty.store.reviews.application.DeleteReview;
import com.pijamaparty.store.reviews.application.ListReviewsForModeration;
import com.pijamaparty.store.reviews.application.ModeratedReview;
import com.pijamaparty.store.reviews.application.RejectReview;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-25 (T-E5-08): review moderation in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/reviews")
@Tag(name = "Back office reviews")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class ReviewModerationController {

    private static final int MAX_PAGE = 10_000;

    private final ListReviewsForModeration list;
    private final ApproveReview approve;
    private final RejectReview reject;
    private final DeleteReview delete;

    ReviewModerationController(ListReviewsForModeration list, ApproveReview approve, RejectReview reject,
                               DeleteReview delete) {
        this.list = list;
        this.approve = approve;
        this.reject = reject;
        this.delete = delete;
    }

    @GetMapping
    @Operation(summary = "The reviews in one status, pending when absent",
        description = ListReviewsForModeration.PAGE_SIZE + " per page, from page 0. Pending ones from the oldest "
            + "submission to the newest, the others newest first.")
    @ApiResponse(responseCode = "200", description = "One page of reviews", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ModerationPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown status or page out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam(defaultValue = "PENDING") ReviewStatus status,
                           @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (list.execute(new ListReviewsForModeration.Query(actor(), status, page))) {
            case ListReviewsForModeration.Result.Listed listed -> ResponseEntity.ok(new ModerationPageResponse(
                listed.reviews().stream().map(ModeratedReviewResponse::of).toList(), listed.hasMore()));
            case ListReviewsForModeration.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a review",
        description = "In any status: it shows on the product page and counts toward the average. Approving it again "
            + "changes nothing. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Approved", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ModeratedReviewResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such review",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> approve(@PathVariable UUID id) {
        return switch (approve.execute(new ApproveReview.Command(actor(), ReviewId.of(id)))) {
            case ApproveReview.Result.Approved done -> ResponseEntity.ok(ModeratedReviewResponse.of(done.review()));
            case ApproveReview.Result.NotFound notFound -> notFound();
            case ApproveReview.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a review",
        description = "In any status: it leaves the product page and the average. Rejecting it again changes nothing. "
            + "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Rejected", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ModeratedReviewResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such review",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> reject(@PathVariable UUID id) {
        return switch (reject.execute(new RejectReview.Command(actor(), ReviewId.of(id)))) {
            case RejectReview.Result.Rejected done -> ResponseEntity.ok(ModeratedReviewResponse.of(done.review()));
            case RejectReview.Result.NotFound notFound -> notFound();
            case RejectReview.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a review", description = "Its author can write a new one. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such review",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> delete(@PathVariable UUID id) {
        return switch (delete.execute(new DeleteReview.Command(actor(), ReviewId.of(id)))) {
            case DeleteReview.Result.Deleted deleted -> ResponseEntity.noContent().build();
            case DeleteReview.Result.NotFound notFound -> notFound();
            case DeleteReview.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Review not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    record ModerationPageResponse(List<ModeratedReviewResponse> items, boolean hasMore) {}

    /**
     * @param authorName the author's first name, as the product page shows it
     * @param text null without one
     * @param updatedAt the last submission
     */
    record ModeratedReviewResponse(UUID id, ReviewedProductResponse product, String authorName, int rating,
                                   @Schema(nullable = true) String text, ReviewStatus status, Instant updatedAt) {

        static ModeratedReviewResponse of(ModeratedReview review) {
            return new ModeratedReviewResponse(review.id().value(), new ReviewedProductResponse(
                review.product().value(),
                review.productLink().map(link -> link.name()).orElse(null),
                review.productLink().map(link -> link.slug()).orElse(null)),
                review.authorName().value(), review.content().rating(), review.content().text().orElse(null),
                review.status(), review.updatedAt());
        }

        @Override
        public String toString() {
            return "ModeratedReviewResponse[id=" + id + ", rating=" + rating + ", status=" + status + ", ***]";
        }
    }

    /** @param name null, as the slug, when the catalog no longer has the product */
    record ReviewedProductResponse(UUID id, @Schema(nullable = true) String name,
                                   @Schema(nullable = true) String slug) {}
}
