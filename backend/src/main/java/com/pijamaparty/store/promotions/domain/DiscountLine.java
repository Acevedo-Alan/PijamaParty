package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-23: an {@code AVAILABLE} cart line as the discount sees it. The ids refer to the catalog and are only compared
 * with a coupon's scope.
 *
 * @param amount the line's price times its quantity
 */
public record DiscountLine(UUID productId, UUID categoryId, Money amount) {

    public DiscountLine {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(amount, "amount");
    }
}
