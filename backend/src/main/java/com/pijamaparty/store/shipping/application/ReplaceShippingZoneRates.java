package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * UC-12: the administrator replaces every shipping cost. The listed provinces get their cost; every other one is left
 * without shipping.
 */
public interface ReplaceShippingZoneRates {

    Result execute(Command command);

    record Command(Actor actor, List<ShippingZoneRates.TypedRate> rates) {

        public Command {
            rates = List.copyOf(rates);
        }
    }

    sealed interface Result {

        record Replaced(ShippingConfiguration configuration) implements Result {}

        /** A cost outside its range, or a province listed twice; nothing is saved. */
        record InvalidProvinces(Set<Province> provinces) implements Result {

            public InvalidProvinces {
                provinces = Collections.unmodifiableSet(EnumSet.copyOf(provinces));
            }
        }

        record Forbidden() implements Result {}
    }
}
