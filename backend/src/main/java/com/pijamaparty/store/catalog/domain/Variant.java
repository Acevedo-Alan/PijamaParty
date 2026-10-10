package com.pijamaparty.store.catalog.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One size and color of a {@link Product}, with its own SKU and price (UC-06). Only its product changes it, so the
 * product's rules (unique size and color, the publish rule) always hold.
 */
public final class Variant {

    /** UC-06: the highest price, ARS 10,000,000. */
    public static final Money MAX_PRICE = Money.of(1_000_000_000L);

    private final VariantId id;
    private final Sku sku;
    private final Size size;
    private int sizePosition;
    private final Color color;
    private int colorPosition;
    private Money price;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private Variant(VariantId id, Sku sku, Size size, int sizePosition, Color color, int colorPosition, Money price,
                    boolean active, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.size = Objects.requireNonNull(size, "size");
        this.sizePosition = requireValidPosition(sizePosition);
        this.color = Objects.requireNonNull(color, "color");
        this.colorPosition = requireValidPosition(colorPosition);
        if (price != null && !isValidPrice(price)) {
            throw new IllegalArgumentException("price out of range");
        }
        this.price = price;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** UC-06: a new variant is active and has no price yet. */
    static Variant create(VariantId id, Sku sku, Size size, int sizePosition, Color color, int colorPosition,
                          Instant now) {
        return new Variant(id, sku, size, sizePosition, color, colorPosition, null, true, now, now);
    }

    /** Rebuilds a stored variant; only persistence adapters call it. */
    public static Variant restore(VariantId id, Sku sku, Size size, int sizePosition, Color color, int colorPosition,
                                  Optional<Money> price, boolean active, Instant createdAt, Instant updatedAt) {
        return new Variant(id, sku, size, sizePosition, color, colorPosition, price.orElse(null), active, createdAt,
            updatedAt);
    }

    /** UC-06: the price for an amount in cents as the administrator sent it; empty when out of range. */
    public static Optional<Money> priceOf(long cents) {
        return cents > 0 && cents <= MAX_PRICE.cents() ? Optional.of(Money.of(cents)) : Optional.empty();
    }

    /** UC-06: from 1 cent to {@link #MAX_PRICE}. */
    public static boolean isValidPrice(Money price) {
        return !price.isZero() && !price.isGreaterThan(MAX_PRICE);
    }

    /** Can be sold once published: active and priced. */
    public boolean isActiveAndPriced() {
        return active && price != null;
    }

    void changePrice(Money newPrice, Instant now) {
        if (!isValidPrice(newPrice)) {
            throw new IllegalArgumentException("price out of range");
        }
        if (!newPrice.equals(price)) {
            price = newPrice;
            updatedAt = now;
        }
    }

    void changeActive(boolean newActive, Instant now) {
        if (newActive != active) {
            active = newActive;
            updatedAt = now;
        }
    }

    void moveTo(int newSizePosition, int newColorPosition, Instant now) {
        if (newSizePosition != sizePosition || newColorPosition != colorPosition) {
            sizePosition = requireValidPosition(newSizePosition);
            colorPosition = requireValidPosition(newColorPosition);
            updatedAt = now;
        }
    }

    Variant copy() {
        return new Variant(id, sku, size, sizePosition, color, colorPosition, price, active, createdAt, updatedAt);
    }

    private static int requireValidPosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        return position;
    }

    public VariantId id() {
        return id;
    }

    public Sku sku() {
        return sku;
    }

    public Size size() {
        return size;
    }

    public int sizePosition() {
        return sizePosition;
    }

    public Color color() {
        return color;
    }

    public int colorPosition() {
        return colorPosition;
    }

    public Optional<Money> price() {
        return Optional.ofNullable(price);
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
