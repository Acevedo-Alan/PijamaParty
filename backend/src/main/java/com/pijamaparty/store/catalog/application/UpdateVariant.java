package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-06: the administrator sets the price of a variant (it can change, never be removed) and activates or deactivates
 * it. A published product keeps at least one active priced variant.
 */
public interface UpdateVariant {

    Result execute(Command command);

    /** Each change is optional; an empty one changes nothing. The price is in cents, as the administrator sent it. */
    record Command(Actor actor, VariantId variantId, Optional<Long> priceCents, Optional<Boolean> active) {

        public Command {
            Objects.requireNonNull(priceCents, "priceCents");
            Objects.requireNonNull(active, "active");
        }
    }

    sealed interface Result {

        /** The whole product, so the back office sees the variant in context. */
        record Updated(ProductView product) implements Result {}

        record NotFound() implements Result {}

        /** Not from 1 cent to ARS 10,000,000. */
        record InvalidPrice() implements Result {}

        /** The last active priced variant of a published product cannot be deactivated: hide the product first. */
        record LastActivePricedVariant() implements Result {}

        record Forbidden() implements Result {}
    }
}
