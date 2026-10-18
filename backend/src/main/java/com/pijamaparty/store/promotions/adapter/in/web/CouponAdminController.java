package com.pijamaparty.store.promotions.adapter.in.web;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.promotions.application.ActivateCoupon;
import com.pijamaparty.store.promotions.application.CouponInput;
import com.pijamaparty.store.promotions.application.CouponView;
import com.pijamaparty.store.promotions.application.CreateCoupon;
import com.pijamaparty.store.promotions.application.DeactivateCoupon;
import com.pijamaparty.store.promotions.application.DeleteCoupon;
import com.pijamaparty.store.promotions.application.ListCoupons;
import com.pijamaparty.store.promotions.application.UpdateCoupon;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponScope;
import com.pijamaparty.store.promotions.domain.CouponType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-11: coupon management in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/coupons")
@Tag(name = "Back office coupons")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class CouponAdminController {

    /** Bounds the typed code; a valid one has at most 20 characters, surrounding spaces aside (UC-10). */
    private static final int MAX_TYPED_CODE = 100;
    private static final int SCOPE_SLACK = 4;

    private final ListCoupons list;
    private final CreateCoupon create;
    private final UpdateCoupon update;
    private final ActivateCoupon activate;
    private final DeactivateCoupon deactivate;
    private final DeleteCoupon delete;

    CouponAdminController(ListCoupons list, CreateCoupon create, UpdateCoupon update, ActivateCoupon activate,
                          DeactivateCoupon deactivate, DeleteCoupon delete) {
        this.list = list;
        this.create = create;
        this.update = update;
        this.activate = activate;
        this.deactivate = deactivate;
        this.delete = delete;
    }

    @GetMapping
    @Operation(summary = "Every coupon, active or not, newest first, with its number of uses")
    @ApiResponse(responseCode = "200", description = "The coupons", content = @Content(mediaType = "application/json",
        array = @ArraySchema(schema = @Schema(implementation = AdminCouponResponse.class))))
    ResponseEntity<?> list() {
        return switch (list.execute(new ListCoupons.Query(actor()))) {
            case ListCoupons.Result.Listed listed ->
                ResponseEntity.ok(listed.coupons().stream().map(AdminCouponResponse::of).toList());
            case ListCoupons.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping
    @Operation(summary = "Create a coupon",
        description = "The code ignores surrounding spaces and case. The coupon is active. automatic (false when absent) "
            + "cannot change later; only an automatic coupon may have a scope. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Created", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminCouponResponse.class)))
    @ApiResponse(responseCode = "409", description = "Another coupon already uses this code; see code",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid fields; see fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> create(@Valid @RequestBody AdminCouponRequest body) {
        return switch (create.execute(new CreateCoupon.Command(actor(), body.input(),
            Boolean.TRUE.equals(body.automatic())))) {
            case CreateCoupon.Result.Created created ->
                ResponseEntity.status(HttpStatus.CREATED).body(AdminCouponResponse.of(created.coupon()));
            case CreateCoupon.Result.CodeAlreadyUsed used -> codeAlreadyUsed(used.code());
            case CreateCoupon.Result.InvalidFields invalid -> invalidFields(invalid.fields());
            case CreateCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a coupon",
        description = "Replaces every field but the state and the uses, also on a used coupon; automatic is ignored. "
            + "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Edited", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminCouponResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such coupon",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Another coupon already uses this code; see code",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid fields; see fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody AdminCouponRequest body) {
        return switch (update.execute(new UpdateCoupon.Command(actor(), CouponId.of(id), body.input()))) {
            case UpdateCoupon.Result.Updated updated -> ResponseEntity.ok(AdminCouponResponse.of(updated.coupon()));
            case UpdateCoupon.Result.NotFound notFound -> notFound();
            case UpdateCoupon.Result.CodeAlreadyUsed used -> codeAlreadyUsed(used.code());
            case UpdateCoupon.Result.InvalidFields invalid -> invalidFields(invalid.fields());
            case UpdateCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a coupon", description = "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Active", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminCouponResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such coupon",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> activate(@PathVariable UUID id) {
        return switch (activate.execute(new ActivateCoupon.Command(actor(), CouponId.of(id)))) {
            case ActivateCoupon.Result.Activated done -> ResponseEntity.ok(AdminCouponResponse.of(done.coupon()));
            case ActivateCoupon.Result.NotFound notFound -> notFound();
            case ActivateCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a coupon", description = "Allowed with uses. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Inactive", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminCouponResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such coupon",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> deactivate(@PathVariable UUID id) {
        return switch (deactivate.execute(new DeactivateCoupon.Command(actor(), CouponId.of(id)))) {
            case DeactivateCoupon.Result.Deactivated done -> ResponseEntity.ok(AdminCouponResponse.of(done.coupon()));
            case DeactivateCoupon.Result.NotFound notFound -> notFound();
            case DeactivateCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a coupon without uses",
        description = "A used coupon can only be deactivated. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such coupon",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The coupon has uses: deactivate it instead",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> delete(@PathVariable UUID id) {
        return switch (delete.execute(new DeleteCoupon.Command(actor(), CouponId.of(id)))) {
            case DeleteCoupon.Result.Deleted deleted -> ResponseEntity.noContent().build();
            case DeleteCoupon.Result.NotFound notFound -> notFound();
            case DeleteCoupon.Result.HasUses hasUses ->
                problem(HttpStatus.CONFLICT, "Coupon has uses",
                    "A coupon with uses cannot be deleted; deactivate it instead.");
            case DeleteCoupon.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> codeAlreadyUsed(String code) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Another coupon already uses this code.");
        problem.setTitle("Code already used");
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> invalidFields(Set<CouponInput.Field> fields) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Code: 3 to 20 letters, digits or hyphens. Percentage: 1 to 100. Amounts: 1 cent to ARS 10,000,000. "
                + "Maximum uses: 1 to 1,000,000. The validity cannot start after it ends. Only an automatic coupon "
                + "has a scope, of at most 100 products and categories.");
        problem.setTitle("Invalid coupon");
        problem.setProperty("fields", fields.stream().map(Enum::name).sorted().toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Coupon not found", null);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * The coupon as typed; the use case checks the rules.
     *
     * @param discountValue the percent, or the cents of a fixed amount
     * @param automatic only read when creating; absent is a code coupon
     * @param scope absent: the whole cart
     */
    record AdminCouponRequest(
        @NotNull @Size(max = MAX_TYPED_CODE) String code,
        @NotNull CouponType type,
        @NotNull Long discountValue,
        @Schema(nullable = true) LocalDate validFrom,
        @Schema(nullable = true) LocalDate validUntil,
        @Schema(nullable = true) Integer maxUses,
        @Schema(nullable = true) Long minimumPurchaseCents,
        @Schema(nullable = true) Boolean automatic,
        @Schema(nullable = true) @Valid ScopeRequest scope) {

        CouponInput input() {
            return new CouponInput(code, type, discountValue, Optional.ofNullable(validFrom),
                Optional.ofNullable(validUntil), Optional.ofNullable(maxUses),
                Optional.ofNullable(minimumPurchaseCents),
                scope == null ? CouponInput.Scope.NONE : scope.input());
        }
    }

    /**
     * UC-23: the products and categories an automatic coupon discounts; repeated ids count once. Each list is only
     * capped to bound the body, at 4 times the domain limit: the use case answers 422 with {@code SCOPE} above it.
     */
    record ScopeRequest(
        @Schema(nullable = true) @Size(max = SCOPE_SLACK * CouponScope.MAX_TARGETS) List<@NotNull UUID> productIds,
        @Schema(nullable = true) @Size(max = SCOPE_SLACK * CouponScope.MAX_TARGETS) List<@NotNull UUID> categoryIds) {

        CouponInput.Scope input() {
            return new CouponInput.Scope(productIds == null ? Set.of() : Set.copyOf(productIds),
                categoryIds == null ? Set.of() : Set.copyOf(categoryIds));
        }
    }

    /**
     * @param discountValue the percent, or the cents of a fixed amount
     * @param scope empty lists: the whole cart
     */
    record AdminCouponResponse(UUID id, String code, CouponType type, long discountValue,
                          @Schema(nullable = true) LocalDate validFrom, @Schema(nullable = true) LocalDate validUntil,
                          @Schema(nullable = true) Integer maxUses, @Schema(nullable = true) Long minimumPurchaseCents,
                          boolean automatic, ScopeResponse scope, boolean active, long uses) {

        static AdminCouponResponse of(CouponView view) {
            return new AdminCouponResponse(view.id().value(), view.code(), view.type(), view.discountValue(),
                view.validFrom().orElse(null), view.validUntil().orElse(null), view.maxUses().orElse(null),
                view.minimumPurchase().map(Money::cents).orElse(null), view.automatic(),
                new ScopeResponse(sorted(view.scope().productIds()), sorted(view.scope().categoryIds())),
                view.active(), view.uses());
        }

        private static List<UUID> sorted(Set<UUID> ids) {
            return ids.stream().sorted().toList();
        }
    }

    record ScopeResponse(List<UUID> productIds, List<UUID> categoryIds) {}
}
