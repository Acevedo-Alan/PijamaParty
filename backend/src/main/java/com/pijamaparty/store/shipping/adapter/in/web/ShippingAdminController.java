package com.pijamaparty.store.shipping.adapter.in.web;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.application.GetShippingConfiguration;
import com.pijamaparty.store.shipping.application.ReplaceShippingZoneRates;
import com.pijamaparty.store.shipping.application.SetFreeShippingThreshold;
import com.pijamaparty.store.shipping.application.ShippingConfiguration;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-12: shipping costs and free shipping in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/shipping")
@Tag(name = "Back office shipping")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class ShippingAdminController {

    private final GetShippingConfiguration get;
    private final ReplaceShippingZoneRates replaceRates;
    private final SetFreeShippingThreshold setThreshold;

    ShippingAdminController(GetShippingConfiguration get, ReplaceShippingZoneRates replaceRates,
                            SetFreeShippingThreshold setThreshold) {
        this.get = get;
        this.replaceRates = replaceRates;
        this.setThreshold = setThreshold;
    }

    @GetMapping
    @Operation(summary = "The shipping cost of every province and the free-shipping threshold")
    @ApiResponse(responseCode = "200", description = "The configuration", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ShippingConfigurationResponse.class)))
    ResponseEntity<?> get() {
        return switch (get.execute(new GetShippingConfiguration.Query(actor()))) {
            case GetShippingConfiguration.Result.Found found ->
                ResponseEntity.ok(ShippingConfigurationResponse.of(found.configuration()));
            case GetShippingConfiguration.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping("/zones")
    @Operation(summary = "Replace every shipping cost",
        description = "The listed provinces get their cost; every other one is left without shipping. "
            + "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Replaced", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ShippingConfigurationResponse.class)))
    @ApiResponse(responseCode = "422", description = "Refused provinces; see provinces",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> replaceZones(@Valid @RequestBody ZonesRequest body) {
        return switch (replaceRates.execute(new ReplaceShippingZoneRates.Command(actor(), body.rates()))) {
            case ReplaceShippingZoneRates.Result.Replaced replaced ->
                ResponseEntity.ok(ShippingConfigurationResponse.of(replaced.configuration()));
            case ReplaceShippingZoneRates.Result.InvalidProvinces invalid -> invalidProvinces(invalid.provinces());
            case ReplaceShippingZoneRates.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping("/free-shipping")
    @Operation(summary = "Set or remove the free-shipping threshold",
        description = "Shipping is free when the subtotal after the discount reaches it; null removes free shipping. "
            + "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Saved", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ShippingConfigurationResponse.class)))
    @ApiResponse(responseCode = "422", description = "Threshold out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> setFreeShipping(@Valid @RequestBody FreeShippingRequest body) {
        var command = new SetFreeShippingThreshold.Command(actor(), Optional.ofNullable(body.thresholdCents()));
        return switch (setThreshold.execute(command)) {
            case SetFreeShippingThreshold.Result.Saved saved ->
                ResponseEntity.ok(ShippingConfigurationResponse.of(saved.configuration()));
            case SetFreeShippingThreshold.Result.InvalidThreshold invalid -> {
                var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The free-shipping threshold goes from 1 cent to ARS 10,000,000.");
                problem.setTitle("Invalid free-shipping threshold");
                yield ResponseEntity.unprocessableEntity().body(problem);
            }
            case SetFreeShippingThreshold.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidProvinces(Set<Province> provinces) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Each province once, with a cost from 0 to ARS 10,000,000.");
        problem.setTitle("Invalid shipping costs");
        problem.setProperty("provinces", provinces.stream().map(Enum::name).toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    /** The costs as typed, at most one entry per province; the use case checks the rules. */
    record ZonesRequest(@NotNull @Size(max = 24) List<@NotNull @Valid ZoneRequest> zones) {

        List<ShippingZoneRates.TypedRate> rates() {
            return zones.stream().map(zone -> new ShippingZoneRates.TypedRate(zone.province(), zone.costCents()))
                .toList();
        }
    }

    record ZoneRequest(@NotNull Province province, @NotNull Long costCents) {}

    /** @param thresholdCents null: no free shipping */
    record FreeShippingRequest(@Schema(nullable = true) Long thresholdCents) {}

    /** @param costCents null: no shipping to that province */
    record ZoneResponse(Province province, @Schema(nullable = true) Long costCents) {}

    /**
     * @param zones every province, in the order of {@link Province}
     * @param freeShippingThresholdCents null: no free shipping
     */
    record ShippingConfigurationResponse(List<ZoneResponse> zones,
                                         @Schema(nullable = true) Long freeShippingThresholdCents) {

        static ShippingConfigurationResponse of(ShippingConfiguration configuration) {
            return new ShippingConfigurationResponse(
                Arrays.stream(Province.values())
                    .map(province -> new ZoneResponse(province,
                        configuration.rates().costOf(province).map(Money::cents).orElse(null)))
                    .toList(),
                configuration.settings().freeShippingThreshold().map(Money::cents).orElse(null));
        }
    }
}
