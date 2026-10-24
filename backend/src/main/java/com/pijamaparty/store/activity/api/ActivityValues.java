package com.pijamaparty.store.activity.api;

import com.pijamaparty.store.platform.domain.Money;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * UC-37: the display text of the values a {@link FieldChange} carries, the same for every module, so an entry reads the
 * same whoever recorded it. A yes/no is {@code true}/{@code false} and a choice its enum name; the screen translates
 * both.
 */
public final class ActivityValues {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);

    private ActivityValues() {
    }

    /** {@code $22.000}, or {@code $22.000,50} with cents. */
    public static String pesos(Money amount) {
        var symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        String whole = new DecimalFormat("#,##0", symbols).format(amount.cents() / 100);
        long cents = amount.cents() % 100;
        return "$" + whole + (cents == 0 ? "" : "," + String.format(Locale.ROOT, "%02d", cents));
    }

    /** A list as one value, such as {@code S, M, L}. */
    public static String list(List<String> values) {
        return String.join(", ", values);
    }

    /** {@code 01/10/2026}. */
    public static String date(LocalDate date) {
        return DATE.format(date);
    }

    /** A signed quantity, such as {@code +5} or {@code -3}. */
    public static String signed(int quantity) {
        return quantity > 0 ? "+" + quantity : Integer.toString(quantity);
    }
}
