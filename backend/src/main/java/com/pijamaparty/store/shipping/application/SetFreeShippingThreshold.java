package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Optional;

/** UC-12: the administrator sets the purchase amount from which shipping is free, or removes free shipping. */
public interface SetFreeShippingThreshold {

    Result execute(Command command);

    /** @param thresholdCents empty: no free shipping */
    record Command(Actor actor, Optional<Long> thresholdCents) {

        public Command {
            Objects.requireNonNull(thresholdCents, "thresholdCents");
        }
    }

    sealed interface Result {

        record Saved(ShippingConfiguration configuration) implements Result {}

        /** Outside 1 cent to ARS 10,000,000; nothing is saved. */
        record InvalidThreshold() implements Result {}

        record Forbidden() implements Result {}
    }
}
