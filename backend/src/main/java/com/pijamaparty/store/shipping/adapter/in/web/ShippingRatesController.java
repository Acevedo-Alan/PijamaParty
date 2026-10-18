package com.pijamaparty.store.shipping.adapter.in.web;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.application.GetShippingRates;
import com.pijamaparty.store.shipping.application.ShippingConfiguration;
import com.pijamaparty.store.shipping.domain.Province;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RNF-31.6 (T-E5-12): the shipping costs of the storefront's FAQ and product page. Only translates. */
@RestController
@RequestMapping("/api/shipping")
@Tag(name = "Shipping")
class ShippingRatesController {

    private final GetShippingRates getRates;

    ShippingRatesController(GetShippingRates getRates) {
        this.getRates = getRates;
    }

    @GetMapping("/rates")
    @Operation(summary = "The shipping cost of each province with shipping and the free-shipping threshold",
        description = "Public. A province left out has no shipping; a cost of 0 is a free shipping to it. Shipping is "
            + "free when the subtotal after the discount reaches the threshold.")
    @ApiResponse(responseCode = "200", description = "The costs", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ShippingRatesResponse.class)))
    ShippingRatesResponse shippingRates() {
        return ShippingRatesResponse.of(getRates.execute());
    }

    record ZoneRateResponse(Province province, long costCents) {}

    /**
     * @param zones only the provinces with shipping, in the order of {@link Province}
     * @param freeShippingThresholdCents null: no free shipping
     */
    record ShippingRatesResponse(List<ZoneRateResponse> zones,
                                 @Schema(nullable = true) Long freeShippingThresholdCents) {

        static ShippingRatesResponse of(ShippingConfiguration configuration) {
            return new ShippingRatesResponse(
                configuration.rates().costs().entrySet().stream()
                    .map(rate -> new ZoneRateResponse(rate.getKey(), rate.getValue().cents()))
                    .toList(),
                configuration.settings().freeShippingThreshold().map(Money::cents).orElse(null));
        }
    }
}
