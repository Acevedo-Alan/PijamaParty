package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.RequestWithdrawal;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-29 (T-E3-25): the withdrawal button. Only translates between HTTP and the use case. */
@RestController
@RequestMapping("/api/withdrawals")
@Tag(name = "Withdrawals")
class WithdrawalController {

    /** Bounds the body; the domain decides what is a number and an email, and answers 422 (RNF-28). */
    private static final int MAX_NUMBER = 20;
    private static final int MAX_TEXT = 300;

    private final RequestWithdrawal requestWithdrawal;

    WithdrawalController(RequestWithdrawal requestWithdrawal) {
        this.requestWithdrawal = requestWithdrawal;
    }

    @PostMapping
    @Operation(summary = "Withdraw from a purchase (botón de arrepentimiento)",
        description = "With the order number (1001 or #1001) and the purchase email, no account needed. Records the "
            + "request and emails its procedure code to the order's email; an order with an active request answers "
            + "its code again and sends nothing. Never reveals whether an order number exists. Requires the CSRF "
            + "token; 3 per hour per order number and IP.")
    @ApiResponse(responseCode = "201", description = "Recorded: the procedure code, also emailed",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = WithdrawalResponse.class)))
    @ApiResponse(responseCode = "200", description = "The order already has an active request: its code",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = WithdrawalResponse.class)))
    @ApiResponse(responseCode = "400", description = "A body that is not {orderNumber, email}, or too long",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The order awaits payment, expired or was cancelled: there is "
        + "no payment to return (problem ORDER_NOT_WITHDRAWABLE, with status)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "No order has this number and email (problem "
        + "NO_MATCHING_ORDER), the same for an unknown number, a wrong email or unusable values",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> request(@Valid @RequestBody WithdrawalRequest body) {
        var command = new RequestWithdrawal.Command(body.orderNumber(), body.email());
        // Nobody holds a token or an account for the order: only the system reads it by its number (RNF-27).
        return switch (CurrentActor.callAsSystem(() -> requestWithdrawal.execute(command))) {
            case RequestWithdrawal.Result.Requested requested ->
                ResponseEntity.status(HttpStatus.CREATED).body(WithdrawalResponse.of(requested.code()));
            case RequestWithdrawal.Result.AlreadyRequested already ->
                ResponseEntity.ok(WithdrawalResponse.of(already.code()));
            case RequestWithdrawal.Result.NoMatchingOrder none -> ResponseEntity.unprocessableEntity()
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "No matching order",
                    "No order has this number and email.", "NO_MATCHING_ORDER"));
            case RequestWithdrawal.Result.NotWithdrawable notWithdrawable -> {
                var problem = problem(HttpStatus.CONFLICT, "Order not withdrawable",
                    "The order has no payment to return: it awaits payment, expired or was cancelled.",
                    "ORDER_NOT_WITHDRAWABLE");
                problem.setProperty("status", notWithdrawable.status());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return problem;
    }

    /** As typed. The email is personal data (R-05), so {@link #toString()} hides it. */
    record WithdrawalRequest(@NotNull @Size(max = MAX_NUMBER) String orderNumber,
                             @NotNull @Size(max = MAX_TEXT) String email) {

        @Override
        public String toString() {
            return "WithdrawalRequest[orderNumber=" + orderNumber + ", email=***]";
        }
    }

    /** The procedure code, {@code ARR-} and 8 characters. */
    record WithdrawalResponse(String code) {

        static WithdrawalResponse of(WithdrawalCode code) {
            return new WithdrawalResponse(code.value());
        }
    }
}
