package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** UC-15 (T-E3b-03): the checkouts whose result may not have been notified. Runs in its own read-only transaction. */
public interface CheckoutsToReconcile {

    /**
     * The checkouts of these providers created before {@code createdBefore} that expire after {@code expiringAfter},
     * of a payment in one of these statuses; the oldest first, at most {@code limit}.
     */
    List<CheckoutToReconcile> due(Set<PaymentProvider> providers, Set<PaymentStatus> statuses, Instant createdBefore,
                                  Instant expiringAfter, int limit);

    record CheckoutToReconcile(PaymentProvider provider, String checkoutId) {

        public CheckoutToReconcile {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(checkoutId, "checkoutId");
        }
    }
}
