package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.ListWithdrawals;
import com.pijamaparty.store.ordering.application.ResolveWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawalSummary;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-29 (T-E3-26): the withdrawal requests in the back office: the list and the resolution. Only translates between
 * HTTP and the use cases.
 */
@RestController
@RequestMapping("/api/admin/withdrawals")
@Tag(name = "Back office withdrawals")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class WithdrawalAdminController {

    private static final int MAX_PAGE = 10_000;

    private final ListWithdrawals listWithdrawals;
    private final ResolveWithdrawal resolveWithdrawal;

    WithdrawalAdminController(ListWithdrawals listWithdrawals, ResolveWithdrawal resolveWithdrawal) {
        this.listWithdrawals = listWithdrawals;
        this.resolveWithdrawal = resolveWithdrawal;
    }

    @GetMapping
    @Operation(summary = "The withdrawal requests, newest first, optionally only those in a status",
        description = "50 per page, from page 0.")
    @ApiResponse(responseCode = "200", description = "One page of requests", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = WithdrawalPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown status or page out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam(required = false) WithdrawalStatus status,
                           @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (listWithdrawals.execute(new ListWithdrawals.Query(actor(), Optional.ofNullable(status), page))) {
            case ListWithdrawals.Result.Listed listed -> ResponseEntity.ok(new WithdrawalPageResponse(
                listed.withdrawals().stream().map(WithdrawalSummaryResponse::of).toList(), listed.hasMore()));
            case ListWithdrawals.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Mark a received withdrawal request as resolved",
        description = "Whatever its order's status; the order is not touched. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Resolved", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = WithdrawalResolvedResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such request (problem WITHDRAWAL_NOT_FOUND)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Already resolved (problem WITHDRAWAL_ALREADY_RESOLVED; "
        + "resolvedAt)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> resolve(@PathVariable UUID id) {
        return switch (resolveWithdrawal.execute(new ResolveWithdrawal.Command(actor(), id))) {
            case ResolveWithdrawal.Result.Resolved resolved -> ResponseEntity.ok(
                new WithdrawalResolvedResponse(WithdrawalStatus.RESOLVED, resolved.resolvedAt()));
            case ResolveWithdrawal.Result.AlreadyResolved already -> {
                var problem = problemDetail(HttpStatus.CONFLICT, "Withdrawal already resolved",
                    "The request was already resolved.", "WITHDRAWAL_ALREADY_RESOLVED");
                problem.setProperty("resolvedAt", already.resolvedAt().toString());
                yield ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
            case ResolveWithdrawal.Result.NotFound notFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                problemDetail(HttpStatus.NOT_FOUND, "Withdrawal not found", "There is no such withdrawal request.",
                    "WITHDRAWAL_NOT_FOUND"));
            case ResolveWithdrawal.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return problem;
    }

    record WithdrawalPageResponse(List<WithdrawalSummaryResponse> items, boolean hasMore) {}

    /**
     * Personal data (R-05): the buyer's name.
     *
     * @param outOfTerm requested after the 10-day term; recorded so the administrator decides
     * @param resolvedAt present exactly when RESOLVED
     */
    record WithdrawalSummaryResponse(UUID id, String code, UUID orderId, long orderNumber, OrderStatus orderStatus,
                                     String buyerName, Instant createdAt, boolean outOfTerm, WithdrawalStatus status,
                                     @Schema(nullable = true) Instant resolvedAt) {

        static WithdrawalSummaryResponse of(WithdrawalSummary withdrawal) {
            return new WithdrawalSummaryResponse(withdrawal.id(), withdrawal.code().value(),
                withdrawal.orderId().value(), withdrawal.orderNumber(), withdrawal.orderStatus(),
                withdrawal.buyerName(), withdrawal.createdAt(), withdrawal.outOfTerm(), withdrawal.status(),
                withdrawal.resolvedAt().orElse(null));
        }

        @Override
        public String toString() {
            return "WithdrawalSummaryResponse[code=" + code + ", orderNumber=" + orderNumber + ", ***]";
        }
    }

    record WithdrawalResolvedResponse(WithdrawalStatus status, Instant resolvedAt) {}
}
