package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.List;
import java.util.Objects;

/**
 * UC-15 (T-E3b-03): MODO retries a notification for only 14 seconds, so every minute the recent checkouts without a
 * result are asked to their provider and processed as notifications ({@link ConfirmPayment}): the same effects and the
 * same idempotency (RNF-20.3). Only providers whose payments are asked for by checkout
 * ({@link PaymentVerifier#verifiesByCheckout()}). Runs as {@code SYSTEM}, outside any transaction (RNF-21).
 */
public interface ReconcileCheckouts {

    Result execute();

    /** What each checkout asked for gave. */
    record Result(List<Reconciled> reconciled) {

        public Result {
            reconciled = List.copyOf(reconciled);
        }
    }

    record Reconciled(PaymentProvider provider, String checkoutId, ConfirmPayment.Result result) {

        public Reconciled {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(result, "result");
        }
    }
}
