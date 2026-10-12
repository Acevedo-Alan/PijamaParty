package com.pijamaparty.store.cart.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A cart priced with the current catalog prices and stock (UC-09, R-01, R-02): each line with its status and any
 * price change since the customer last saw it, and the totals. Only {@link LineStatus#AVAILABLE} lines can be bought,
 * so only they count in the subtotal.
 */
public record PricedCart(List<PricedLine> lines, Money subtotal, Money discount, Money total) {

    private static final PricedCart EMPTY = new PricedCart(List.of(), Money.zero(), Money.zero(), Money.zero());

    public PricedCart {
        lines = List.copyOf(lines);
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(total, "total");
        if (discount.isGreaterThan(subtotal) || !total.equals(subtotal.minus(discount))) {
            throw new IllegalArgumentException("the discount is at most the subtotal, and the total is what is left");
        }
    }

    public static PricedCart empty() {
        return EMPTY;
    }

    /**
     * Prices {@code cart}; {@code sellable} has the variants the shop sells now, the others are unavailable. Without
     * a discount: the coupon's is calculated on this subtotal, then {@link #withDiscount} takes it off.
     */
    public static PricedCart of(Cart cart, Map<VariantId, SellableVariant> sellable) {
        List<PricedLine> lines = cart.items().stream()
            .map(item -> PricedLine.of(item, Optional.ofNullable(sellable.get(item.variantId()))))
            .toList();
        Money subtotal = lines.stream()
            .filter(line -> line.status() == LineStatus.AVAILABLE)
            .map(line -> line.unitPrice().times(line.item().quantity()))
            .reduce(Money.zero(), Money::plus);
        Money discount = Money.zero();
        return new PricedCart(lines, subtotal, discount, subtotal.minus(discount));
    }

    /** UC-10: the coupon's {@code discount}, at most the subtotal, comes off the total. */
    public PricedCart withDiscount(Money discount) {
        return new PricedCart(lines, subtotal, discount, subtotal.minus(discount));
    }

    /**
     * One line of the cart as it can be bought now.
     *
     * @param unitPrice the current price; the last seen price when the catalog no longer sells the variant
     * @param previousUnitPrice the last seen price when the current one differs: a change to announce
     * @param available the units that can be bought now
     */
    public record PricedLine(CartItem item, Money unitPrice, Optional<Money> previousUnitPrice, LineStatus status,
                             int available) {

        public PricedLine {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(unitPrice, "unitPrice");
            Objects.requireNonNull(previousUnitPrice, "previousUnitPrice");
            Objects.requireNonNull(status, "status");
        }

        static PricedLine of(CartItem item, Optional<SellableVariant> sellable) {
            if (sellable.isEmpty()) {
                return new PricedLine(item, item.lastSeenPrice(), Optional.empty(), LineStatus.UNAVAILABLE, 0);
            }
            Money price = sellable.get().price();
            int available = sellable.get().available();
            Optional<Money> previous = price.equals(item.lastSeenPrice())
                ? Optional.empty()
                : Optional.of(item.lastSeenPrice());
            LineStatus status = available == 0
                ? LineStatus.UNAVAILABLE
                : available < item.quantity() ? LineStatus.INSUFFICIENT_STOCK : LineStatus.AVAILABLE;
            return new PricedLine(item, price, previous, status, available);
        }
    }

    public enum LineStatus {
        /** Sold, and its quantity is within the stock. */
        AVAILABLE,
        /** Some units are left, fewer than its quantity; the customer lowers it ("Solo queda 1"). */
        INSUFFICIENT_STOCK,
        /** Sold out, or the catalog no longer sells it. */
        UNAVAILABLE
    }
}
