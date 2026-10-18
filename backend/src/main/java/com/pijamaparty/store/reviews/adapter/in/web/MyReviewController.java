package com.pijamaparty.store.reviews.adapter.in.web;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.reviews.application.GetMyReview;
import com.pijamaparty.store.reviews.application.MyReview;
import com.pijamaparty.store.reviews.application.SubmitReview;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewForm;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-24 (T-E5-06): the signed-in customer's review of a product. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/account/reviews")
@Tag(name = "Customer account")
@ApiResponse(responseCode = "401", description = "Not signed in as a customer",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class MyReviewController {

    /** Bounds the body only: the use case answers 422 with {@code text} above the domain limit. */
    private static final int TEXT_SLACK = 4;

    private final GetMyReview get;
    private final SubmitReview submit;

    MyReviewController(GetMyReview get, SubmitReview submit) {
        this.get = get;
        this.submit = submit;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Whether the customer may review a product, and its review of it",
        description = "eligible: the customer has an order of the product that is paid and not cancelled, so it may "
            + "submit or edit its review; false for an unknown product. review: null when it has not reviewed it.")
    @ApiResponse(responseCode = "200", description = "The customer's standing on the product",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MyReviewAnswer.class)))
    ResponseEntity<?> myReview(@PathVariable UUID productId) {
        return switch (get.execute(new GetMyReview.Query(actor(), ProductId.of(productId)))) {
            case GetMyReview.Result.Found found -> ResponseEntity.ok(new MyReviewAnswer(found.eligible(),
                found.review().map(MyReviewResponse::of).orElse(null)));
            case GetMyReview.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Review a product, or edit the review",
        description = "Only a customer who bought the product (an order paid and not cancelled), checked on every "
            + "submission. The review waits for moderation, also after an edit. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "The customer's first review of the product",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MyReviewResponse.class)))
    @ApiResponse(responseCode = "200", description = "The review was replaced and went back to moderation",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MyReviewResponse.class)))
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "NOT_A_BUYER, or INVALID_REVIEW with the invalid fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> submitReview(@PathVariable UUID productId, @Valid @RequestBody ReviewRequest body) {
        var command = new SubmitReview.Command(actor(), ProductId.of(productId), body.toForm());
        return switch (submit.execute(command)) {
            case SubmitReview.Result.Created created ->
                ResponseEntity.status(HttpStatus.CREATED).body(MyReviewResponse.of(created.review()));
            case SubmitReview.Result.Replaced replaced -> ResponseEntity.ok(MyReviewResponse.of(replaced.review()));
            case SubmitReview.Result.InvalidReview invalid -> invalidReview(invalid.fields());
            case SubmitReview.Result.NotABuyer notABuyer -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Not a buyer", "Only customers who bought this product can review it.", "NOT_A_BUYER");
            case SubmitReview.Result.ProductNotFound notFound ->
                problem(HttpStatus.NOT_FOUND, "Product not found", null, "PRODUCT_NOT_FOUND");
            case SubmitReview.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidReview(Set<ReviewForm.Field> fields) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Rating: a whole number from " + ReviewContent.MIN_RATING + " to " + ReviewContent.MAX_RATING
                + ". Text: optional, at most " + ReviewContent.MAX_TEXT + " characters.");
        problem.setTitle("Invalid review");
        problem.setProperty("problem", "INVALID_REVIEW");
        problem.setProperty("fields", fields.stream()
            .sorted(Comparator.naturalOrder())
            .map(MyReviewController::fieldName)
            .toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static String fieldName(ReviewForm.Field field) {
        return switch (field) {
            case RATING -> "rating";
            case TEXT -> "text";
        };
    }

    private static ResponseEntity<ProblemDetail> notSignedIn() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                         String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * The review as typed; the use case checks the rules.
     *
     * @param rating a whole number from 1 to 5
     * @param text optional; blank is none
     */
    record ReviewRequest(@Schema(implementation = Integer.class, minimum = "1", maximum = "5") BigDecimal rating,
                         @Schema(nullable = true) @Size(max = TEXT_SLACK * ReviewContent.MAX_TEXT) String text) {

        ReviewForm toForm() {
            return new ReviewForm(rating, text);
        }

        @Override
        public String toString() {
            return "ReviewRequest[rating=" + rating + ", text=***]";
        }
    }

    /** @param review null when the customer has not reviewed the product */
    record MyReviewAnswer(boolean eligible, @Schema(nullable = true) MyReviewResponse review) {}

    /** @param updatedAt the last submission */
    record MyReviewResponse(int rating, @Schema(nullable = true) String text, ReviewStatus status, Instant updatedAt) {

        static MyReviewResponse of(MyReview review) {
            return new MyReviewResponse(review.rating(), review.text().orElse(null), review.status(),
                review.updatedAt());
        }

        @Override
        public String toString() {
            return "MyReviewResponse[rating=" + rating + ", status=" + status + ", text=***]";
        }
    }
}
