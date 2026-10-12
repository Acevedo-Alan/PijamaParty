package com.pijamaparty.store.cart.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * One line of a {@link Cart}: a variant and how many units of it. {@code lastSeenPrice} is the variant price the
 * customer last saw, so a price change can be announced (UC-09).
 */
public record CartItem(CartItemId id, VariantId variantId, int quantity, Money lastSeenPrice, Instant createdAt) {

    public CartItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(lastSeenPrice, "lastSeenPrice");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity < 1 || quantity > Cart.MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity out of range: " + quantity);
        }
        if (lastSeenPrice.isZero()) {
            throw new IllegalArgumentException("a variant price is above 0");
        }
    }

    CartItem withQuantity(int newQuantity) {
        return new CartItem(id, variantId, newQuantity, lastSeenPrice, createdAt);
    }

    CartItem withQuantityAndPrice(int newQuantity, Money price) {
        return new CartItem(id, variantId, newQuantity, price, createdAt);
    }
}
