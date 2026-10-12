package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.domain.Province;
import java.util.Optional;

/**
 * Strategy (ARCHITECTURE.md section 5): the base cost of shipping to a province, before free shipping. The zone table
 * of UC-12 now, a carrier API later.
 */
public interface ShippingCostCalculator {

    /** Empty: there is no shipping to that province. */
    Optional<Money> costTo(Province province);
}
