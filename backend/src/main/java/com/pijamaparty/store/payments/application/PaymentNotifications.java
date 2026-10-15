package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import java.time.Instant;

/**
 * UC-15 (T-E3-15, RNF-20.3): the record of the provider's notifications, one per verified status of each of its
 * payments. Joins the caller's transaction, which must exist, so a notification whose processing fails is not recorded.
 */
public interface PaymentNotifications {

    /**
     * Records the notification of the provider's payment with the status it was verified with, unless it is already
     * recorded. A concurrent caller recording the same one waits until the first transaction ends.
     *
     * @param orderNumber the order the payment is for: its own, or the one of the checkout it paid (T-E3b-03)
     * @return true when recorded now, so the notification is to be processed; false when it already was
     */
    boolean recordFirst(PaymentProvider provider, VerifiedPayment verified, long orderNumber, Instant receivedAt);
}
