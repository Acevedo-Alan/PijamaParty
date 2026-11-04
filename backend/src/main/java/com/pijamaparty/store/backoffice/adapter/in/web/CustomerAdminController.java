package com.pijamaparty.store.backoffice.adapter.in.web;

import com.pijamaparty.store.backoffice.application.ListCustomers;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-34 (T-E8-10): "Clientes" of the back office. Only translates between HTTP and the use case. */
@RestController
@RequestMapping("/api/admin/customers")
@Tag(name = "Back office customers")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class CustomerAdminController {

    /** Bounds the offset; 10,000 pages of 50 are far beyond the customers of the shop. */
    private static final int MAX_PAGE = 10_000;

    private final ListCustomers listCustomers;

    CustomerAdminController(ListCustomers listCustomers) {
        this.listCustomers = listCustomers;
    }

    @GetMapping
    @Operation(summary = "The registered customers, newest first, with their number of orders",
        description = "50 per page, from page 0. `q` is 2 to 100 characters that the customer's name or email "
            + "contains, ignoring case and accents, every character literal. `from` and `to` are store dates "
            + "(America/Argentina/Buenos_Aires) of the registration, both inclusive. Only the orders linked to the "
            + "account count. The search is personal data: it is never logged.")
    @ApiResponse(responseCode = "200", description = "One page of customers", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CustomerPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Page out of range, INVALID_SEARCH (1 character or over 100), a "
        + "date that is not yyyy-MM-dd, or INVALID_PERIOD (from after to)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam(required = false) String q,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> to,
                           @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        var query = new ListCustomers.Query(CurrentActor.get().orElseThrow(), Optional.ofNullable(q), from, to, page);
        return switch (listCustomers.execute(query)) {
            case ListCustomers.Result.Listed listed -> ResponseEntity.ok(new CustomerPageResponse(
                listed.customers().stream().map(CustomerRowResponse::of).toList(), listed.hasMore()));
            case ListCustomers.Result.InvalidSearch invalid -> problem("Invalid search",
                "2 to 100 characters of the customer's name or email.", "INVALID_SEARCH");
            case ListCustomers.Result.InvalidPeriod invalid -> problem("Invalid period",
                "`from` must not be after `to`.", "INVALID_PERIOD");
            case ListCustomers.Result.Forbidden forbidden ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
        };
    }

    private static ResponseEntity<ProblemDetail> problem(String title, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return ResponseEntity.badRequest().body(problem);
    }

    record CustomerPageResponse(List<CustomerRowResponse> items, boolean hasMore) {}

    /** Personal data: never logged. */
    record CustomerRowResponse(UUID id, String name, String email, Instant registeredAt, long orders) {

        static CustomerRowResponse of(ListCustomers.CustomerRow row) {
            return new CustomerRowResponse(row.id(), row.name(), row.email(), row.registeredAt(), row.orders());
        }

        @Override
        public String toString() {
            return "CustomerRowResponse[id=" + id + ", name=<hidden>, email=<hidden>]";
        }
    }
}
