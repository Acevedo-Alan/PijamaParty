package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link PaymentNotifications}: one notification per provider, provider's payment and status, as the primary
 * key. Transactions are not modeled: a recorded notification stays recorded.
 */
public class InMemoryPaymentNotifications implements PaymentNotifications {

    /** What the table would hold of a notification. */
    public record Recorded(PaymentProvider provider, String externalPaymentId, VerifiedStatus status, long orderNumber) {}

    private final Set<Recorded> recorded = ConcurrentHashMap.newKeySet();

    @Override
    public synchronized boolean recordFirst(PaymentProvider provider, VerifiedPayment verified, long orderNumber,
                                            Instant receivedAt) {
        boolean seen = recorded.stream().anyMatch(notification -> notification.provider() == provider
            && notification.externalPaymentId().equals(verified.externalId())
            && notification.status() == verified.status());
        return !seen && recorded.add(new Recorded(provider, verified.externalId(), verified.status(),
            orderNumber));
    }

    public List<Recorded> recorded() {
        return List.copyOf(recorded);
    }
}
