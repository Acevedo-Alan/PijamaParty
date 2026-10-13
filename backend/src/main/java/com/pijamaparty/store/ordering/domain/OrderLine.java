package com.pijamaparty.store.ordering.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * One item of an order: the variant as it was sold (R-04), so later catalog changes do not alter the order.
 *
 * @param variantId refers to the catalog module
 * @param quantity from 1 to 1,000,000 (UC-07)
 */
public record OrderLine(UUID variantId, String sku, String productName, String size, String color, Money unitPrice,
                        int quantity) {

    public static final int MAX_QUANTITY = 1_000_000;

    public OrderLine {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.isZero()) {
            throw new IllegalArgumentException("a variant price is above 0");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity out of range: " + quantity);
        }
    }

    public Money amount() {
        return unitPrice.times(quantity);
    }
}
