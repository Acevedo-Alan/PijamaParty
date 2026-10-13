package com.pijamaparty.store.inventory.adapter.in.web;

import com.pijamaparty.store.inventory.application.AdjustStock;
import com.pijamaparty.store.inventory.application.ListStockMovements;
import com.pijamaparty.store.inventory.application.StockMovementView;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-07: stock adjustments and history in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Back office stock")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class StockAdminController {

    /** The canonical text of a UUID, as the frontend's crypto.randomUUID() writes it. */
    private static final Pattern UUID_TEXT =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Bounds the offset; 10,000 pages of 50 movements are far beyond the history of a variant. */
    private static final int MAX_PAGE = 10_000;

    /** The note is only capped to bound the body, at 4 times the domain limit: the domain answers 422 (UC-07). */
    private static final int SLACK = 4;

    private final AdjustStock adjust;
    private final ListStockMovements listMovements;

    StockAdminController(AdjustStock adjust, ListStockMovements listMovements) {
        this.adjust = adjust;
        this.listMovements = listMovements;
    }

    @PostMapping("/stock-adjustments")
    @Operation(summary = "Add stock to a variant or subtract it",
        description = "quantity is signed, from -10,000 to 10,000 and not 0; note is optional, up to 200 characters. "
            + "The first movement of a variant is its initial load. Retrying with the same Idempotency-Key, quantity "
            + "and note changes nothing. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Adjusted", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdjustmentResponse.class)))
    @ApiResponse(responseCode = "200", description = "Already adjusted with this key: the current stock",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdjustmentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key or body",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such variant",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "See problem: WOULD_GO_NEGATIVE or WOULD_EXCEED_MAXIMUM; the "
        + "current stock is in onHand. WOULD_GO_BELOW_RESERVED also has in reserved the units held by orders awaiting "
        + "payment",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "See problem: INVALID_QUANTITY, INVALID_NOTE or KEY_REUSED",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> adjust(
        @Parameter(description = "A UUID per adjustment, repeated on retries") @RequestHeader("Idempotency-Key")
        String idempotencyKey,
        @Valid @RequestBody AdjustmentRequest body) {
        if (!UUID_TEXT.matcher(idempotencyKey).matches()) {
            return problem(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", "The Idempotency-Key must be a UUID.");
        }
        var command = new AdjustStock.Command(actor(), VariantId.of(body.variantId()),
            AdjustmentKey.of(UUID.fromString(idempotencyKey)), body.quantity(), Optional.ofNullable(body.note()));
        return switch (adjust.execute(command)) {
            case AdjustStock.Result.Adjusted adjusted -> ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdjustmentResponse(body.variantId(), adjusted.newOnHand()));
            case AdjustStock.Result.AlreadyAdjusted already ->
                ResponseEntity.ok(new AdjustmentResponse(body.variantId(), already.onHand()));
            case AdjustStock.Result.WouldGoNegative negative -> outOfRange("WOULD_GO_NEGATIVE",
                "Stock cannot go below 0.", negative.currentOnHand());
            case AdjustStock.Result.WouldGoBelowReserved belowReserved -> {
                var problem = outOfRangeProblem("WOULD_GO_BELOW_RESERVED",
                    "Stock cannot go below the units reserved by orders awaiting payment.",
                    belowReserved.currentOnHand());
                problem.setProperty("reserved", belowReserved.reserved());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
            case AdjustStock.Result.WouldExceedMaximum maximum -> outOfRange("WOULD_EXCEED_MAXIMUM",
                "Stock is at most " + StockLevel.MAX_ON_HAND + " units.", maximum.currentOnHand());
            case AdjustStock.Result.InvalidQuantity invalid -> invalidAdjustment("INVALID_QUANTITY",
                "The quantity must be from -" + StockLevel.MAX_ADJUSTMENT + " to " + StockLevel.MAX_ADJUSTMENT
                    + ", and not 0.");
            case AdjustStock.Result.InvalidNote invalid -> invalidAdjustment("INVALID_NOTE",
                "The note must be one line of at most " + MovementNote.MAX_LENGTH + " characters.");
            case AdjustStock.Result.KeyReused reused -> invalidAdjustment("KEY_REUSED",
                "This Idempotency-Key was used with another adjustment.");
            case AdjustStock.Result.UnknownVariant unknown -> variantNotFound();
            case AdjustStock.Result.Forbidden forbidden -> forbidden();
        };
    }

    @GetMapping("/variants/{id}/stock-movements")
    @Operation(summary = "The stock of a variant and its movements, newest first",
        description = "50 per page, from page 0. Each movement has its date, user, quantity and note (the reason).")
    @ApiResponse(responseCode = "200", description = "One page of movements", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = MovementPageResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such variant",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> movements(@PathVariable UUID id,
                                @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (listMovements.execute(new ListStockMovements.Query(actor(), VariantId.of(id), page))) {
            case ListStockMovements.Result.Listed listed -> ResponseEntity.ok(new MovementPageResponse(id,
                listed.onHand(), listed.movements().stream().map(MovementResponse::of).toList(), listed.hasMore()));
            case ListStockMovements.Result.UnknownVariant unknown -> variantNotFound();
            case ListStockMovements.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> outOfRange(String reason, String detail, int onHand) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(outOfRangeProblem(reason, detail, onHand));
    }

    private static ProblemDetail outOfRangeProblem(String reason, String detail, int onHand) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Stock out of range");
        problem.setProperty("problem", reason);
        problem.setProperty("onHand", onHand);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> invalidAdjustment(String reason, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle("Invalid adjustment");
        problem.setProperty("problem", reason);
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> variantNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Variant not found", null);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /** {@code quantity} is signed: positive adds, negative subtracts. */
    record AdjustmentRequest(@NotNull UUID variantId, @NotNull Integer quantity,
                             @Size(max = SLACK * MovementNote.MAX_LENGTH) String note) {}

    record AdjustmentResponse(UUID variantId, int onHand) {}

    record MovementPageResponse(UUID variantId, int onHand, List<MovementResponse> items, boolean hasMore) {}

    /** {@code note} is the reason; {@code userId} and {@code userName} are null when unknown. */
    record MovementResponse(UUID id, MovementType type, int quantity, String note, UUID userId, String userName,
                            Instant createdAt) {

        static MovementResponse of(StockMovementView view) {
            return new MovementResponse(view.id().value(), view.type(), view.quantity(), view.note().orElse(null),
                view.userId().map(UserId::value).orElse(null), view.userName().orElse(null), view.createdAt());
        }
    }
}
