package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake of {@link Coupons}: percentage coupons with an optional minimum purchase, which apply unless they are
 * {@link #reject}ed for another reason. Codes are matched as promotions does: ignoring surrounding spaces and case,
 * and an automatic coupon's code is unknown. A cart's discount is that of promotions (UC-23): an automatic coupon may
 * be scoped to categories, and only the largest discount applies, the code coupon on a tie, then the oldest automatic
 * coupon.
 */
public class InMemoryCoupons implements Coupons {

    private final Map<CouponId, Definition> coupons = new ConcurrentHashMap<>();
    private final AtomicLong created = new AtomicLong();

    /** A coupon of {@code percent} percent, rounded down, for a subtotal of at least {@code minimum}. */
    public CouponId percentage(String code, int percent, Money minimum) {
        return add(code, percent, minimum, false, Set.of());
    }

    public CouponId percentage(String code, int percent) {
        return percentage(code, percent, Money.zero());
    }

    /** UC-23: an automatic coupon of the whole cart, newer than those added before it. */
    public CouponId automatic(String code, int percent, Money minimum) {
        return add(code, percent, minimum, true, Set.of());
    }

    /**
     * UC-23: an automatic coupon of only the lines of {@code categoryIds}, whose subtotal must reach {@code minimum};
     * newer than those added before it.
     */
    public CouponId automatic(String code, int percent, Money minimum, Set<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            throw new IllegalArgumentException("a scope has targets");
        }
        return add(code, percent, minimum, true, categoryIds);
    }

    private CouponId add(String code, int percent, Money minimum, boolean automatic, Set<UUID> categoryIds) {
        CouponId id = CouponId.of(UUID.randomUUID());
        coupons.put(id, new Definition(code.toUpperCase(Locale.ROOT), percent, minimum, automatic,
            Set.copyOf(categoryIds), created.incrementAndGet(), Optional.empty()));
        return id;
    }

    /** From now on the coupon does not apply, for {@code reason}: neither applicable nor below the minimum. */
    public void reject(CouponId id, CouponCheck.Status reason) {
        if (reason == CouponCheck.Status.APPLICABLE || reason == CouponCheck.Status.BELOW_MINIMUM) {
            throw new IllegalArgumentException("not a fixed reason: " + reason);
        }
        coupons.computeIfPresent(id, (key, coupon) -> coupon.rejectedFor(reason));
    }

    /** The administrator deleted it (UC-11). */
    public void delete(CouponId id) {
        coupons.remove(id);
    }

    @Override
    public Optional<CouponCheck> byCode(String code, Money subtotal) {
        String typed = code.strip().toUpperCase(Locale.ROOT);
        return coupons.entrySet().stream()
            .filter(entry -> !entry.getValue().automatic() && entry.getValue().code().equals(typed))
            .findFirst()
            .map(entry -> entry.getValue().check(entry.getKey(), subtotal));
    }

    @Override
    public CartDiscount discount(List<Line> lines, Optional<CouponId> coupon) {
        Money subtotal = sum(lines, Set.of());
        Optional<CouponCheck> check = coupon
            .flatMap(id -> Optional.ofNullable(coupons.get(id)).filter(held -> !held.automatic())
                .map(held -> held.check(id, subtotal)));
        Optional<CartDiscount.Applied> best = check.filter(CouponCheck::applies)
            .map(held -> new CartDiscount.Applied(held.id(), held.code(), false, held.discount()));
        List<Map.Entry<CouponId, Definition>> automatic = coupons.entrySet().stream()
            .filter(entry -> entry.getValue().automatic())
            .sorted(Comparator.comparingLong(entry -> entry.getValue().created()))
            .toList();
        for (Map.Entry<CouponId, Definition> entry : automatic) {
            Definition definition = entry.getValue();
            CouponCheck candidate = definition.check(entry.getKey(), sum(lines, definition.categoryIds()));
            boolean larger = best.map(current -> candidate.discount().isGreaterThan(current.amount())).orElse(true);
            if (candidate.applies() && !candidate.discount().isZero() && larger) {
                best = Optional.of(new CartDiscount.Applied(entry.getKey(), definition.code(), true,
                    candidate.discount()));
            }
        }
        return new CartDiscount(check, best);
    }

    /** The subtotal of the lines of {@code categoryIds}, or of all of them without categories. */
    private static Money sum(List<Line> lines, Set<UUID> categoryIds) {
        return lines.stream()
            .filter(line -> categoryIds.isEmpty() || categoryIds.contains(line.categoryId()))
            .map(Line::amount)
            .reduce(Money.zero(), Money::plus);
    }

    private record Definition(String code, int percent, Money minimum, boolean automatic, Set<UUID> categoryIds,
                              long created, Optional<CouponCheck.Status> rejection) {

        Definition rejectedFor(CouponCheck.Status reason) {
            return new Definition(code, percent, minimum, automatic, categoryIds, created, Optional.of(reason));
        }

        CouponCheck check(CouponId id, Money subtotal) {
            if (rejection.isPresent()) {
                return new CouponCheck(id, code, rejection.get(), Money.zero(), Money.zero());
            }
            if (subtotal.isLessThan(minimum)) {
                return new CouponCheck(id, code, CouponCheck.Status.BELOW_MINIMUM, Money.zero(),
                    minimum.minus(subtotal));
            }
            return new CouponCheck(id, code, CouponCheck.Status.APPLICABLE, subtotal.percent(percent), Money.zero());
        }
    }
}
