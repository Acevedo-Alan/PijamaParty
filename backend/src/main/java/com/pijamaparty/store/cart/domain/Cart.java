package com.pijamaparty.store.cart.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A guest's or a customer's cart (UC-08, UC-09): one line per variant, in the order they were added, and at most one
 * coupon (UC-10). It holds the stock rule: the quantity of a line never exceeds the variant's available stock (on
 * hand − reserved) at the moment it is added or changed. The cart reserves nothing; the reservation starts with the
 * payment (E3).
 */
public final class Cart {

    /** The stock maximum of UC-07: no line can ever need more. */
    public static final int MAX_QUANTITY = 1_000_000;

    private final CartId id;
    private final CartOwner owner;
    private final List<CartItem> items;
    private Optional<CouponId> coupon;
    private final Instant createdAt;
    private Instant updatedAt;

    private Cart(CartId id, CartOwner owner, List<CartItem> items, Optional<CouponId> coupon, Instant createdAt,
                 Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.items = new ArrayList<>(items);
        this.coupon = Objects.requireNonNull(coupon, "coupon");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (new HashSet<>(items.stream().map(CartItem::variantId).toList()).size() != items.size()
            || new HashSet<>(items.stream().map(CartItem::id).toList()).size() != items.size()) {
            throw new IllegalArgumentException("one line per variant, each with its own id");
        }
    }

    /** UC-08: the first product added creates the cart. */
    public static Cart open(CartId id, CartOwner owner, Instant at) {
        return new Cart(id, owner, List.of(), Optional.empty(), at, at);
    }

    /** Rebuilds a stored cart; {@code items} in the order they were added. */
    public static Cart of(CartId id, CartOwner owner, List<CartItem> items, Optional<CouponId> coupon,
                          Instant createdAt, Instant updatedAt) {
        return new Cart(id, owner, items, coupon, createdAt, updatedAt);
    }

    /** Whether {@code quantity} can be asked for when adding (UC-08). */
    public static boolean isValidAddedQuantity(int quantity) {
        return quantity >= 1 && quantity <= MAX_QUANTITY;
    }

    /** Whether a line can be changed to {@code quantity}, where 0 removes it (UC-09). */
    public static boolean isValidChangedQuantity(int quantity) {
        return quantity >= 0 && quantity <= MAX_QUANTITY;
    }

    /**
     * UC-08: adds {@code quantity} units of the variant, to its line if it has one. The line then remembers
     * {@code price} as the last seen price. Rejected when the line would exceed {@code available}.
     *
     * @param newItemId the id of the line if the variant is new to the cart
     */
    public AddOutcome add(CartItemId newItemId, VariantId variantId, int quantity, int available, Money price,
                          Instant at) {
        if (!isValidAddedQuantity(quantity)) {
            throw new IllegalArgumentException("quantity out of range: " + quantity);
        }
        Optional<CartItem> line = itemOf(variantId);
        long total = (long) line.map(CartItem::quantity).orElse(0) + quantity;
        if (total > available || total > MAX_QUANTITY) {
            return new AddOutcome.ExceedsStock(available);
        }
        CartItem added;
        if (line.isPresent()) {
            added = line.get().withQuantityAndPrice((int) total, price);
            items.set(items.indexOf(line.get()), added);
        } else {
            added = new CartItem(newItemId, variantId, quantity, price, at);
            items.add(added);
        }
        updatedAt = at;
        return new AddOutcome.Added(added);
    }

    /**
     * UC-09: changes the quantity of a line; 0 removes it, whatever the stock. Any other quantity is rejected when
     * above {@code available}.
     */
    public ChangeOutcome changeQuantity(CartItemId itemId, int quantity, int available, Instant at) {
        if (!isValidChangedQuantity(quantity)) {
            throw new IllegalArgumentException("quantity out of range: " + quantity);
        }
        Optional<CartItem> line = item(itemId);
        if (line.isEmpty()) {
            return new ChangeOutcome.NoSuchItem();
        }
        if (quantity == 0) {
            items.remove(line.get());
            updatedAt = at;
            return new ChangeOutcome.Removed();
        }
        if (quantity > available) {
            return new ChangeOutcome.ExceedsStock(available);
        }
        CartItem changed = line.get().withQuantity(quantity);
        items.set(items.indexOf(line.get()), changed);
        updatedAt = at;
        return new ChangeOutcome.Changed(changed);
    }

    /** UC-09: removes a line; returns whether the cart had it. */
    public boolean remove(CartItemId itemId, Instant at) {
        Optional<CartItem> line = item(itemId);
        line.ifPresent(found -> {
            items.remove(found);
            updatedAt = at;
        });
        return line.isPresent();
    }

    /**
     * UC-09: the customer saw the current prices, so every line the shop still sells remembers its current price and
     * no price change is announced anymore. Returns whether any line changed.
     */
    public boolean acknowledgePrices(Map<VariantId, SellableVariant> sellable, Instant at) {
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            CartItem line = items.get(i);
            SellableVariant variant = sellable.get(line.variantId());
            if (variant != null && !variant.price().equals(line.lastSeenPrice())) {
                items.set(i, line.withQuantityAndPrice(line.quantity(), variant.price()));
                changed = true;
            }
        }
        if (changed) {
            updatedAt = at;
        }
        return changed;
    }

    /**
     * UC-19 (T-E4-04): the guest's cart joins this customer's cart at sign-in. A variant in both adds the quantities,
     * capped at {@code available}, and keeps this cart's line and last seen price; a line already above the stock is
     * never lowered. A variant only in the guest's comes with its line (id, last seen price and when it was added),
     * capped at {@code available}. A guest line of a variant the shop no longer sells, or with nothing available, is
     * left out. This cart keeps its coupon; it takes the guest's only when it has none. Lines stay in the order they
     * were added. Returns whether this cart changed.
     *
     * @param sellable what the shop sells now of the guest's variants
     */
    public boolean merge(Cart guest, Map<VariantId, SellableVariant> sellable, Instant at) {
        if (!(owner instanceof CartOwner.Customer) || !(guest.owner instanceof CartOwner.Guest)) {
            throw new IllegalArgumentException("a guest's cart joins a customer's");
        }
        boolean changed = false;
        for (CartItem line : guest.items) {
            SellableVariant variant = sellable.get(line.variantId());
            if (variant == null || variant.available() == 0) {
                continue;
            }
            Optional<CartItem> own = itemOf(line.variantId());
            if (own.isPresent()) {
                long sum = (long) own.get().quantity() + line.quantity();
                int merged = (int) Math.max(own.get().quantity(), Math.min(sum, variant.available()));
                if (merged != own.get().quantity()) {
                    items.set(items.indexOf(own.get()), own.get().withQuantity(merged));
                    changed = true;
                }
            } else {
                if (item(line.id()).isPresent()) {
                    throw new IllegalArgumentException("each line has its own id");
                }
                items.add(new CartItem(line.id(), line.variantId(), Math.min(line.quantity(), variant.available()),
                    line.lastSeenPrice(), line.createdAt()));
                changed = true;
            }
        }
        if (coupon.isEmpty() && guest.coupon.isPresent()) {
            coupon = guest.coupon;
            changed = true;
        }
        if (changed) {
            items.sort(Comparator.comparing(CartItem::createdAt));
            updatedAt = at;
        }
        return changed;
    }

    /** UC-10: the coupon, which promotions found applicable, replaces the one the cart had. */
    public void applyCoupon(CouponId couponId, Instant at) {
        coupon = Optional.of(Objects.requireNonNull(couponId, "couponId"));
        updatedAt = at;
    }

    /** UC-10: returns whether the cart had a coupon. */
    public boolean removeCoupon(Instant at) {
        if (coupon.isEmpty()) {
            return false;
        }
        coupon = Optional.empty();
        updatedAt = at;
        return true;
    }

    /**
     * UC-13: the order placed from this cart now holds its items, so the cart loses them and its coupon. Returns
     * whether it had any.
     */
    public boolean clear(Instant at) {
        if (items.isEmpty() && coupon.isEmpty()) {
            return false;
        }
        items.clear();
        coupon = Optional.empty();
        updatedAt = at;
        return true;
    }

    public Optional<CartItem> item(CartItemId itemId) {
        return items.stream().filter(item -> item.id().equals(itemId)).findFirst();
    }

    private Optional<CartItem> itemOf(VariantId variantId) {
        return items.stream().filter(item -> item.variantId().equals(variantId)).findFirst();
    }

    /** An independent copy, for stores that hand out copies like a database does. */
    public Cart copy() {
        return new Cart(id, owner, items, coupon, createdAt, updatedAt);
    }

    public CartId id() {
        return id;
    }

    public CartOwner owner() {
        return owner;
    }

    /** In the order they were added. */
    public List<CartItem> items() {
        return List.copyOf(items);
    }

    /** UC-10: the applied coupon, whether or not it still applies; promotions decides that each time it is priced. */
    public Optional<CouponId> coupon() {
        return coupon;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** The last change: the daily cleanup deletes carts idle for more than 30 days (T-E2-04). */
    public Instant updatedAt() {
        return updatedAt;
    }

    public sealed interface AddOutcome {

        record Added(CartItem item) implements AddOutcome {}

        /** The line would hold more than {@code available}, the units left. */
        record ExceedsStock(int available) implements AddOutcome {}
    }

    public sealed interface ChangeOutcome {

        record Changed(CartItem item) implements ChangeOutcome {}

        record Removed() implements ChangeOutcome {}

        record ExceedsStock(int available) implements ChangeOutcome {}

        record NoSuchItem() implements ChangeOutcome {}
    }
}
