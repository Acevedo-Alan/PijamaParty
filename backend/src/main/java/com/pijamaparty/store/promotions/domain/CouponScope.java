package com.pijamaparty.store.promotions.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UC-23, UC-11: the products and categories an automatic coupon discounts, at most {@link #MAX_TARGETS} in all. A line
 * matches when its product is one of the products or belongs to one of the categories. Without targets it is the
 * whole cart. The ids refer to the catalog: one it no longer has simply matches no line.
 */
public record CouponScope(Set<UUID> productIds, Set<UUID> categoryIds) {

    public static final int MAX_TARGETS = 100;

    public static final CouponScope WHOLE_CART = new CouponScope(Set.of(), Set.of());

    public CouponScope {
        productIds = Set.copyOf(productIds);
        categoryIds = Set.copyOf(categoryIds);
        if (productIds.size() + categoryIds.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("a scope has at most " + MAX_TARGETS + " targets");
        }
    }

    public boolean isWholeCart() {
        return productIds.isEmpty() && categoryIds.isEmpty();
    }

    public boolean matches(DiscountLine line) {
        Objects.requireNonNull(line, "line");
        return isWholeCart() || productIds.contains(line.productId()) || categoryIds.contains(line.categoryId());
    }
}
