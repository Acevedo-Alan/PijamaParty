package com.pijamaparty.store.payments.adapter.in.scheduler;

import com.pijamaparty.store.payments.application.ConfirmPayment;
import com.pijamaparty.store.payments.application.ReconcileCheckouts;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-15 (T-E3b-03): every minute ({@code store.payments.reconciliation.cron}) the recent checkouts without a result
 * are asked to their provider, as {@code SYSTEM} (RNF-27), in case the notification never arrived. Logs carry the
 * provider, the checkout id and the order number only (R-05).
 */
@Component
class CheckoutReconciliation {

    private static final Logger log = LoggerFactory.getLogger(CheckoutReconciliation.class);

    private final ReconcileCheckouts reconcileCheckouts;

    CheckoutReconciliation(ReconcileCheckouts reconcileCheckouts) {
        this.reconcileCheckouts = reconcileCheckouts;
    }

    @Scheduled(cron = "${store.payments.reconciliation.cron}", zone = TimeConfiguration.STORE_ZONE_ID)
    void run() {
        for (ReconcileCheckouts.Reconciled checkout : CurrentActor.callAsSystem(reconcileCheckouts::execute)
            .reconciled()) {
            switch (checkout.result()) {
                case ConfirmPayment.Result.Approved approved -> log.info(
                    "Reconciliation: {} checkout {} approved order {}", checkout.provider(), checkout.checkoutId(),
                    approved.orderNumber());
                case ConfirmPayment.Result.Rejected rejected -> log.info(
                    "Reconciliation: {} checkout {} of order {} was rejected", checkout.provider(),
                    checkout.checkoutId(), rejected.orderNumber());
                case ConfirmPayment.Result.AmountMismatch mismatch -> log.error(
                    "Reconciliation: {} checkout {} of order {} was approved for {} instead of {}: the order is not "
                        + "paid", checkout.provider(), checkout.checkoutId(), mismatch.orderNumber(), mismatch.paid(),
                    mismatch.expected());
                case ConfirmPayment.Result.AlreadyApproved already -> { }
                case ConfirmPayment.Result.Unchanged unchanged -> { }
                case ConfirmPayment.Result.UnknownPayment unknown -> { }
                case ConfirmPayment.Result.AlreadyProcessed processed -> { }
                case ConfirmPayment.Result.NotFoundAtProvider notFound -> { }
                case ConfirmPayment.Result.ProviderUnavailable unavailable -> { }
            }
        }
    }
}
