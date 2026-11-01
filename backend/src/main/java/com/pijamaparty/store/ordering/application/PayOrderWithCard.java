package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-14 (T-E3-29): pays an order awaiting payment with the card the buyer typed on the store's page, in MercadoPago's
 * card form. The amount and the payer's email are the order's (R-02). Run as the buyer that holds the order's tracking
 * token, outside any transaction: no database connection waits for the provider (RNF-21).
 */
public interface PayOrderWithCard {

    Result execute(Command command);

    /** @param idempotencyKey the buyer's key of this submit (RNF-20): the same key never charges twice */
    record Command(TrackingToken token, UUID idempotencyKey, Card card) {

        public Command {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(card, "card");
        }
    }

    /**
     * What MercadoPago's card form answered for the card; the cardholder's ID is personal data (R-05), hidden by
     * {@link #toString()} and never stored.
     */
    record Card(String token, String paymentMethodId, Optional<String> issuerId, int installments,
                String identificationType, String identificationNumber) {

        public Card {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(paymentMethodId, "paymentMethodId");
            Objects.requireNonNull(issuerId, "issuerId");
            Objects.requireNonNull(identificationType, "identificationType");
            Objects.requireNonNull(identificationNumber, "identificationNumber");
        }

        @Override
        public String toString() {
            return "Card[***]";
        }
    }

    /** Why a card was rejected, as the storefront explains it. */
    enum Rejection {
        INSUFFICIENT_FUNDS,
        BAD_SECURITY_CODE,
        BAD_EXPIRY_DATE,
        BAD_CARD_DATA,
        CALL_FOR_AUTHORIZE,
        CARD_DISABLED,
        DUPLICATED,
        MAX_ATTEMPTS,
        HIGH_RISK,
        OTHER
    }

    sealed interface Result {

        /** Approved: the order is paid. */
        record Approved() implements Result {}

        /** Pending or in review: the result arrives later through the webhook (UC-15). */
        record InProcess() implements Result {}

        /** Rejected: another card can be tried while the reservation lasts. */
        record Rejected(Rejection reason) implements Result {

            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }

        /** The provider could not be reached: nothing was charged, and paying can be retried. */
        record ProviderUnavailable() implements Result {}

        /** No longer awaiting payment, its reservation is over, or it is not paid by card on the store's page. */
        record NotPayable() implements Result {}

        /** No order has this token. */
        record NotFound() implements Result {}
    }
}
