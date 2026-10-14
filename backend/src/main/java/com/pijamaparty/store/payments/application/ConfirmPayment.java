package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/**
 * UC-15: a provider notified that one of its payments changed. The payment is asked to the provider (R-06), never
 * taken from the notification, and recorded on the order's payment; an approval publishes
 * {@link com.pijamaparty.store.payments.api.PaymentApproved}. Runs as {@code SYSTEM} and outside any transaction, so no
 * database connection waits for the provider (RNF-21). Repeating it has no further effects (RNF-20.3, RNF-20.5).
 */
public interface ConfirmPayment {

    Result execute(Command command);

    /** @param externalPaymentId the provider's id of the payment, as notified */
    record Command(PaymentProvider provider, String externalPaymentId) {

        public Command {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(externalPaymentId, "externalPaymentId");
        }
    }

    sealed interface Result {

        /** The order's payment is approved; the order was told. */
        record Approved(long orderNumber) implements Result {}

        /** The payment had already been approved: nothing happened again. */
        record AlreadyApproved(long orderNumber) implements Result {}

        /** The buyer's attempt was rejected; the order keeps its reservation until it expires. */
        record Rejected(long orderNumber) implements Result {}

        /** Pending, or nothing the store acts on. */
        record Unchanged(long orderNumber) implements Result {}

        /** Approved for an amount other than the order's: nothing changed. */
        record AmountMismatch(long orderNumber, Money expected, Money paid) implements Result {

            public AmountMismatch {
                Objects.requireNonNull(expected, "expected");
                Objects.requireNonNull(paid, "paid");
            }
        }

        /** The provider's payment is for an order this store has no payment of. */
        record UnknownPayment() implements Result {}

        /** The provider's payment was already notified with this status: nothing happened again (RNF-20.3). */
        record AlreadyProcessed() implements Result {}

        /** The provider does not know the payment. */
        record NotFoundAtProvider() implements Result {}

        /** The provider could not be asked; the notification can be processed again later. */
        record ProviderUnavailable() implements Result {}
    }
}
