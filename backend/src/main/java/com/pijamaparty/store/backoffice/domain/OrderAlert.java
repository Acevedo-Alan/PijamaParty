package com.pijamaparty.store.backoffice.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** UC-18: what the back-office order detail warns the administrator about, most urgent first. */
public enum OrderAlert {

    /** The payment was approved after the order expired or was cancelled: refund it from the provider's panel. */
    PAID_AFTER_CLOSING,

    /** The buyer's payment-confirmed email bounced: call the buyer (T-E3-22). */
    BUYER_EMAIL_BOUNCED,

    /** The buyer did not get the payment-confirmed email: call the buyer (decided in T-E3-10). */
    BUYER_EMAIL_FAILED;

    /** Why the buyer did not get the payment-confirmed email. */
    public enum BuyerEmailProblem {
        FAILED,
        BOUNCED
    }

    /** The alerts of an order, most urgent first. */
    public static List<OrderAlert> of(boolean paidAfterClosing, Optional<BuyerEmailProblem> buyerEmail) {
        List<OrderAlert> alerts = new ArrayList<>();
        if (paidAfterClosing) {
            alerts.add(PAID_AFTER_CLOSING);
        }
        buyerEmail.ifPresent(problem -> alerts.add(switch (problem) {
            case FAILED -> BUYER_EMAIL_FAILED;
            case BOUNCED -> BUYER_EMAIL_BOUNCED;
        }));
        return List.copyOf(alerts);
    }
}
