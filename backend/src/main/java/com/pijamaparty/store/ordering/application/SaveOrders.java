package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Outbound port: writes new orders with their items (UC-14), within the caller's transaction. */
@FunctionalInterface
public interface SaveOrders {

    /** Inserts the order, awaiting payment, and its items in their order. */
    void insert(NewOrder order);

    /**
     * An order as the checkout places it, awaiting payment.
     *
     * @param trackingTokenHash {@code AccessToken.hash()} of its tracking token; the token itself is never stored
     * @param userId the customer's; empty for a guest
     * @param lines in the cart's order, one per variant
     * @param coupon the applied coupon, when there is a discount
     */
    record NewOrder(OrderId id, long number, String trackingTokenHash, Optional<UserId> userId, BuyerDetails buyer,
                    ShippingAddress address, Province province, List<OrderLine> lines, OrderAmounts amounts,
                    Optional<AppliedCoupon> coupon, PaymentProvider paymentProvider, String termsVersion,
                    Instant createdAt, Instant reservationExpiresAt) {

        private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

        public NewOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(province, "province");
            lines = List.copyOf(lines);
            Objects.requireNonNull(amounts, "amounts");
            Objects.requireNonNull(coupon, "coupon");
            Objects.requireNonNull(paymentProvider, "paymentProvider");
            Objects.requireNonNull(termsVersion, "termsVersion");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
            if (trackingTokenHash == null || !SHA_256_HEX.matcher(trackingTokenHash).matches()) {
                throw new IllegalArgumentException("not a SHA-256 hash in lowercase hex");
            }
            if (lines.isEmpty() || new HashSet<>(lines.stream().map(OrderLine::variantId).toList()).size()
                != lines.size()) {
                throw new IllegalArgumentException("an order has one line per variant, at least one");
            }
            if (!amounts.subtotal().equals(lines.stream().map(OrderLine::amount).reduce(Money.zero(), Money::plus))) {
                throw new IllegalArgumentException("the subtotal is the sum of the lines");
            }
            if (!reservationExpiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("the reservation expires after the order is placed");
            }
        }
    }

    /** UC-10: the coupon whose discount the order got; {@code code} as stored. */
    record AppliedCoupon(UUID couponId, String code) {

        public AppliedCoupon {
            Objects.requireNonNull(couponId, "couponId");
            Objects.requireNonNull(code, "code");
        }
    }
}
