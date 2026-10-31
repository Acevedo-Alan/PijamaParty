package com.pijamaparty.store.backoffice.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalInt;

/** UC-33: how a period's amount changed against the period before, in whole percents. */
public final class Variation {

    private Variation() {
    }

    /** Empty ("—") when nothing sold in the earlier period; otherwise rounded half up, e.g. +50 or -12. */
    public static OptionalInt percent(Money current, Money previous) {
        if (previous.isZero()) {
            return OptionalInt.empty();
        }
        BigDecimal change = BigDecimal.valueOf(current.cents() - previous.cents())
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(previous.cents()), 0, RoundingMode.HALF_UP);
        return OptionalInt.of(change.intValueExact());
    }
}
