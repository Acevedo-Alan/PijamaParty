package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The data of a coupon as the administrator typed it (UC-11); the use cases validate it. The code is taken ignoring
 * surrounding spaces and case, as a customer types it (UC-10).
 *
 * @param discountValue the percent, or the cents of a fixed amount
 */
public record CouponInput(String code, CouponType type, long discountValue, Optional<LocalDate> validFrom,
                          Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                          Optional<Long> minimumPurchaseCents) {

    public CouponInput {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
        Objects.requireNonNull(maxUses, "maxUses");
        Objects.requireNonNull(minimumPurchaseCents, "minimumPurchaseCents");
    }

    /** The coupon with this data, or every field that breaks a rule. */
    Parsed parse(CouponId id, boolean active) {
        Optional<CouponCode> parsedCode = CouponCode.parse(code);
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        if (parsedCode.isEmpty()) {
            invalid.add(Field.CODE);
        }
        // Money is never negative, so a minimum below 1 cent is refused before it becomes one.
        Optional<Money> minimum = minimumPurchaseCents.filter(cents -> cents > 0).map(Money::of);
        if (minimumPurchaseCents.isPresent() && minimum.isEmpty()) {
            invalid.add(Field.MINIMUM_PURCHASE);
        }
        Coupon.invalidFields(type, discountValue, validFrom, validUntil, maxUses, minimum)
            .forEach(field -> invalid.add(Field.valueOf(field.name())));
        if (!invalid.isEmpty()) {
            return new Parsed.Invalid(invalid);
        }
        return new Parsed.Valid(Coupon.of(id, parsedCode.get(), type, discountValue, validFrom, validUntil, maxUses,
            minimum, active));
    }

    public enum Field {
        /** Not 3 to 20 letters, digits or hyphens. */
        CODE,
        /** A percent outside 1 to 100, or an amount outside 1 cent to ARS 10,000,000. */
        DISCOUNT_VALUE,
        /** The validity starts after it ends. */
        VALIDITY,
        /** Outside 1 to 1,000,000. */
        MAX_USES,
        /** Outside 1 cent to ARS 10,000,000. */
        MINIMUM_PURCHASE
    }

    sealed interface Parsed {

        record Valid(Coupon coupon) implements Parsed {}

        record Invalid(Set<Field> fields) implements Parsed {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }
    }
}
