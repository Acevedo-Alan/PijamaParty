package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.CheckoutForm;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponStatus;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UC-13, UC-14: the buyer confirms the checkout. The cart becomes an order awaiting payment, with its stock reserved
 * for 30 minutes (UC-16), all in one transaction as the buyer (RNF-27). A customer's order also leaves its phone, and
 * its address if asked, on the account (UC-21). The amounts are the server's (R-02). The same
 * {@code Idempotency-Key} repeated with the same content gets the first order again, and nothing is repeated (T-E3-14,
 * RNF-20.2).
 */
public interface PlaceOrder {

    Result execute(Command command);

    /**
     * @param actor          a guest or a customer bound to the new order's tracking token; any other actor has no cart
     * @param idempotencyKey the request's {@code Idempotency-Key}: a repetition gets the first order (T-E3-14)
     * @param saveAddress    a customer adds the order's address to its address book (UC-21); ignored for a guest
     */
    record Command(Actor actor, UUID idempotencyKey, CheckoutForm form, Province province,
                   PaymentProvider paymentProvider, boolean acceptedTerms, boolean saveAddress) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(province, "province");
            Objects.requireNonNull(paymentProvider, "paymentProvider");
        }
    }

    sealed interface Result {

        /** The order awaits payment; {@code total} = subtotal − discount + shipping. */
        record Placed(OrderId orderId, long number, Money total, Instant reservationExpiresAt) implements Result {}

        /** Each field of the form that breaks a rule of UC-13. */
        record InvalidBuyerData(Set<CheckoutForm.Field> fields) implements Result {

            public InvalidBuyerData {
                fields = Set.copyOf(fields);
            }
        }

        record TermsNotAccepted() implements Result {}

        /** The provider is switched off at the checkout (T-E3b-02: MODO, by configuration). */
        record PaymentProviderNotEnabled(PaymentProvider provider) implements Result {}

        /** No cart or no items; also an actor that cannot own a cart. */
        record EmptyCart() implements Result {}

        /** An item is not available, or has a price change the buyer has not acknowledged (UC-09). */
        record CartNotReady() implements Result {}

        /** The cart's coupon no longer applies (UC-10); {@code status} says why. */
        record CouponNotApplicable(CouponStatus status) implements Result {}

        record NoShipping(Province province) implements Result {}

        /** The first short variant, and the units it has available; nothing was reserved or written. */
        record InsufficientStock(UUID variantId, int available) implements Result {}

        /** A request with the same {@code Idempotency-Key} is still being processed (T-E3-14): try again shortly. */
        record KeyInUse() implements Result {}

        /** The {@code Idempotency-Key} was already used with a different content (T-E3-14). */
        record KeyReused() implements Result {}
    }
}
