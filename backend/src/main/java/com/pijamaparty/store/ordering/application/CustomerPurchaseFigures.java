package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Outbound read port (UC-34): the figures of the orders linked to a customer, within the caller's transaction. */
public interface CustomerPurchaseFigures {

    /** Zeros and no last purchase for a customer without orders. */
    Figures figuresOf(UserId customer);

    /**
     * @param orders every linked order, in any status
     * @param purchases those whose status {@code isBought()}
     * @param spent the sum of the purchases' totals
     * @param lastPurchase the creation instant of the newest purchase
     */
    record Figures(long orders, long purchases, Money spent, Optional<Instant> lastPurchase) {

        public static final Figures NONE = new Figures(0, 0, Money.zero(), Optional.empty());

        public Figures {
            Objects.requireNonNull(spent, "spent");
            Objects.requireNonNull(lastPurchase, "lastPurchase");
        }
    }
}
