package com.pijamaparty.store.backoffice.adapter.in.web;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import com.pijamaparty.store.backoffice.application.GetCustomerProfile;
import com.pijamaparty.store.backoffice.application.ListCustomers;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderRow;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-34 (T-E8-10, T-E8-11): "Clientes" of the back office and a customer's profile. Only translates HTTP. */
@RestController
@RequestMapping("/api/admin/customers")
@Tag(name = "Back office customers")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class CustomerAdminController {

    /** Bounds a search before its use case reads it (2 to 100 characters, RNF-28, T-E8-14), well above that. */
    private static final int MAX_SEARCH_TYPED = 1_000;

    /** Bounds the offset; 10,000 pages of 50 are far beyond the customers of the shop. */
    private static final int MAX_PAGE = 10_000;

    private final ListCustomers listCustomers;
    private final GetCustomerProfile getCustomerProfile;

    CustomerAdminController(ListCustomers listCustomers, GetCustomerProfile getCustomerProfile) {
        this.listCustomers = listCustomers;
        this.getCustomerProfile = getCustomerProfile;
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
    ResponseEntity<?> list(@RequestParam(required = false) @Size(max = MAX_SEARCH_TYPED) String q,
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

    @GetMapping("/{id}")
    @Operation(summary = "A customer's profile: contact, linked providers, addresses, orders and purchase figures",
        description = "The orders come 20 per page, newest first, from `ordersPage` 0; `hasMoreOrders` tells whether "
            + "another page follows. Only the orders linked to the account count. `purchases`, `spentCents` and "
            + "`lastPurchaseAt` count the orders paid and neither cancelled nor expired. Opening the profile "
            + "(`ordersPage` 0) records CUSTOMER_VIEWED in the activity log, with the customer's id only.")
    @ApiResponse(responseCode = "200", description = "The profile", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = CustomerProfileResponse.class)))
    @ApiResponse(responseCode = "400", description = "ordersPage out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such customer (problem CUSTOMER_NOT_FOUND): unknown, "
        + "deleted, or the administrator",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> profile(@PathVariable UUID id,
                              @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int ordersPage) {
        var query = new GetCustomerProfile.Query(CurrentActor.get().orElseThrow(), id, ordersPage);
        return switch (getCustomerProfile.execute(query)) {
            case GetCustomerProfile.Result.Shown shown ->
                ResponseEntity.ok(CustomerProfileResponse.of(shown.profile(), shown.orders()));
            case GetCustomerProfile.Result.NotFound notFound -> {
                var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "There is no such customer.");
                problem.setTitle("Customer not found");
                problem.setProperty("problem", "CUSTOMER_NOT_FOUND");
                yield ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
            }
            case GetCustomerProfile.Result.Forbidden forbidden ->
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

    /**
     * Personal data (R-05): {@link #toString()} prints only the id.
     *
     * @param phone digits only, null when the customer saved none
     * @param providers the linked sign-in providers, in the order they were linked
     * @param addresses the main one first
     * @param orders one page, newest first
     * @param orderCount every linked order, in any status
     * @param purchases the orders paid and neither cancelled nor expired
     * @param lastPurchaseAt null without purchases
     */
    record CustomerProfileResponse(UUID id, String name, String email, boolean emailVerified,
                                   @Schema(nullable = true) String phone, Instant registeredAt,
                                   List<SignInProvider> providers, List<CustomerAddressResponse> addresses,
                                   List<CustomerOrderResponse> orders, boolean hasMoreOrders, long orderCount,
                                   long purchases, long spentCents, @Schema(nullable = true) Instant lastPurchaseAt) {

        static CustomerProfileResponse of(CustomerProfile profile, OrderHistory history) {
            return new CustomerProfileResponse(profile.id(), profile.name(), profile.email(), profile.emailVerified(),
                profile.phone().orElse(null), profile.registeredAt(), profile.providers(),
                profile.addresses().stream().map(CustomerAddressResponse::of).toList(),
                history.orders().stream().map(CustomerOrderResponse::of).toList(), history.hasMore(),
                history.orderCount(), history.purchases(), history.spent().cents(),
                history.lastPurchase().orElse(null));
        }

        @Override
        public String toString() {
            return "CustomerProfileResponse[id=" + id + ", ***]";
        }
    }

    /**
     * Personal data (R-05): {@link #toString()} prints nothing of it.
     *
     * @param province one of the 24 jurisdictions, e.g. SANTA_FE
     */
    record CustomerAddressResponse(UUID id, String street, String number, @Schema(nullable = true) String floor,
                                @Schema(nullable = true) String apartment, String city, String province,
                                String postalCode, boolean primary) {

        static CustomerAddressResponse of(SavedAddress address) {
            return new CustomerAddressResponse(address.id(), address.street(), address.number(),
                address.floor().orElse(null), address.apartment().orElse(null), address.city(), address.province(),
                address.postalCode(), address.primary());
        }

        @Override
        public String toString() {
            return "CustomerAddressResponse[id=" + id + ", ***]";
        }
    }

    record CustomerOrderResponse(UUID id, long number, Instant createdAt, Status status, long totalCents) {

        static CustomerOrderResponse of(OrderRow row) {
            return new CustomerOrderResponse(row.orderId(), row.number(), row.createdAt(), row.status(),
                row.total().cents());
        }
    }
}
