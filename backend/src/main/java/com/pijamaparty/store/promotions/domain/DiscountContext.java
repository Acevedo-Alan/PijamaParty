package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * UC-23: what a coupon's rules check: the subtotal it discounts, today (the store date, R-07) and its uses so far.
 * Built from the cart's lines, a scope narrows it to the matching ones; built from a subtotal alone, it has no lines
 * to narrow, which is enough for a coupon without a scope.
 */
public final class DiscountContext {

    private final Money subtotal;
    private final Optional<List<DiscountLine>> lines;
    private final LocalDate today;
    private final long uses;

    private DiscountContext(Money subtotal, Optional<List<DiscountLine>> lines, LocalDate today, long uses) {
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
        this.lines = lines;
        this.today = Objects.requireNonNull(today, "today");
        if (uses < 0) {
            throw new IllegalArgumentException("uses cannot be negative");
        }
        this.uses = uses;
    }

    /** The cart's {@code AVAILABLE} lines; the subtotal is their sum. */
    public static DiscountContext ofLines(List<DiscountLine> lines, LocalDate today, long uses) {
        List<DiscountLine> copy = List.copyOf(lines);
        return new DiscountContext(sum(copy), Optional.of(copy), today, uses);
    }

    /** A subtotal without its lines (UC-10): only a coupon without a scope can be checked against it. */
    public static DiscountContext ofSubtotal(Money subtotal, LocalDate today, long uses) {
        return new DiscountContext(subtotal, Optional.empty(), today, uses);
    }

    /** The same context with only the lines that {@code matches} accepts, and their subtotal. */
    public DiscountContext narrowedTo(Predicate<DiscountLine> matches) {
        List<DiscountLine> matching = lines
            .orElseThrow(() -> new IllegalStateException("a subtotal without its lines cannot be narrowed"))
            .stream().filter(matches).toList();
        return new DiscountContext(sum(matching), Optional.of(matching), today, uses);
    }

    private static Money sum(List<DiscountLine> lines) {
        return lines.stream().map(DiscountLine::amount).reduce(Money.zero(), Money::plus);
    }

    public Money subtotal() {
        return subtotal;
    }

    public LocalDate today() {
        return today;
    }

    public long uses() {
        return uses;
    }
}
