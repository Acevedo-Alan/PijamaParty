package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.cart.domain.PricedCart.LineStatus;
import com.pijamaparty.store.cart.domain.PricedCart.PricedLine;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The full state of a cart that every cart response returns (UC-08, UC-09, UC-10): its items in the order they were
 * added, each with what the panel shows of its variant, priced with the current catalog prices and stock, its coupon,
 * and the totals calculated by the server.
 */
public record CartView(List<ItemView> items, Optional<CouponView> coupon, Money subtotal, Money discount,
                       Money total) {

    private static final CartView EMPTY = of(PricedCart.empty(), Optional.empty(), Map.of());

    public CartView {
        items = List.copyOf(items);
        Objects.requireNonNull(coupon, "coupon");
    }

    public static CartView empty() {
        return EMPTY;
    }

    /**
     * {@code cart} already carries the discount of {@code coupon} when it applies; {@code descriptions} has what the
     * catalog says of each line's variant.
     */
    public static CartView of(PricedCart cart, Optional<CouponCheck> coupon,
                              Map<VariantId, ItemDescription> descriptions) {
        return new CartView(
            cart.lines().stream()
                .map(line -> ItemView.of(line, Optional.ofNullable(descriptions.get(line.item().variantId()))))
                .toList(),
            coupon.map(CouponView::of), cart.subtotal(), cart.discount(), cart.total());
    }

    /** As {@link PricedLine}; {@code description} is empty when the catalog no longer has the variant. */
    public record ItemView(CartItemId id, VariantId variantId, int quantity, Money unitPrice,
                           Optional<Money> previousUnitPrice, LineStatus status, int available,
                           Optional<ItemDescription> description) {

        public ItemView {
            Objects.requireNonNull(description, "description");
        }

        static ItemView of(PricedLine line, Optional<ItemDescription> description) {
            return new ItemView(line.item().id(), line.item().variantId(), line.item().quantity(), line.unitPrice(),
                line.previousUnitPrice(), line.status(), line.available(), description);
        }
    }

    /** The applied coupon and whether it applies now; as {@link CouponCheck}. */
    public record CouponView(String code, CouponCheck.Status status, Money missing) {

        static CouponView of(CouponCheck check) {
            return new CouponView(check.code(), check.status(), check.missing());
        }
    }
}
