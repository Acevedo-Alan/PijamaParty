package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.ShippingCosts;
import com.pijamaparty.store.shipping.api.ShippingQuote;
import com.pijamaparty.store.shipping.domain.Province;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-12, UC-13: the base cost of the calculator, with the shop's free shipping on top. */
@Service
class ShippingCostsService implements ShippingCosts {

    private final ShippingCostCalculator calculator;
    private final LoadShippingSettings loadSettings;

    ShippingCostsService(ShippingCostCalculator calculator, LoadShippingSettings loadSettings) {
        this.calculator = calculator;
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingQuote quote(com.pijamaparty.store.shipping.api.Province province, Money subtotalAfterDiscount) {
        Objects.requireNonNull(province, "province");
        Objects.requireNonNull(subtotalAfterDiscount, "subtotalAfterDiscount");
        return calculator.costTo(Province.valueOf(province.name()))
            .<ShippingQuote>map(cost -> new ShippingQuote.Cost(
                loadSettings.loadSettings().chargeFor(cost, subtotalAfterDiscount)))
            .orElseGet(ShippingQuote.NoShipping::new);
    }
}
