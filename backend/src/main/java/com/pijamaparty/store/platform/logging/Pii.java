package com.pijamaparty.store.platform.logging;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Masks personal data before it reaches a log line (RNF-04, R-05). National ids, addresses and emails are never
 * logged in clear: pass them through these methods, and pass any free text that may contain them (exception
 * messages, provider responses) through {@link #scrub(String)}.
 *
 * <pre>{@code log.warn("Email bounced for order {} ({})", order.number(), Pii.email(order.email()));}</pre>
 */
public final class Pii {

    static final String MASK = "***";

    /** Bounded and without nested quantifiers, so untrusted text cannot trigger a ReDoS. */
    private static final Pattern EMAIL =
        Pattern.compile("[A-Za-z0-9._%+-]{1,64}@([A-Za-z0-9-]{1,63}\\.[A-Za-z0-9.-]{0,190}[A-Za-z0-9])");
    /** CUIT/CUIL: 2 digits, 8 digits (the national id), 1 check digit; dashes optional. */
    private static final Pattern TAX_ID =
        Pattern.compile("(?<![\\w.-])\\d{2}-?\\d{8}-?\\d(?![\\w.-])");
    /** DNI: 7 or 8 digits, optionally with thousands dots (12.345.678). Not part of a longer token such as a UUID. */
    private static final Pattern NATIONAL_ID =
        Pattern.compile("(?<![\\w.-])\\d{1,2}\\.?\\d{3}\\.?\\d{3}(?![\\w.-])");

    private static final int MAX_CAUSES = 10;
    private static final List<String> DATABASE_EXCEPTION_PACKAGES = List.of(
        "org.springframework.dao.", "org.springframework.jdbc.", "org.springframework.orm.",
        "org.hibernate.", "jakarta.persistence.", "org.postgresql.");

    private Pii() {
    }

    /** Stands in for the original exception in logs; its message starts with the original type. */
    static final class ScrubbedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ScrubbedException(String message, Throwable cause) {
            super(message, cause, false, true);
        }
    }

    /** Keeps only the domain, which helps diagnose bounces: {@code ana@gmail.com -> ***@gmail.com}. */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        return at < 0 ? MASK : MASK + email.substring(at).toLowerCase(Locale.ROOT);
    }

    public static String nationalId(String nationalId) {
        return nationalId == null ? null : MASK;
    }

    public static String address(String address) {
        return address == null ? null : MASK;
    }

    /**
     * A copy of the exception chain for logging: same types and stack traces, messages scrubbed. Exception messages
     * often carry input values (for example a unique-constraint violation with the email).
     */
    public static Throwable scrubException(Throwable throwable) {
        return scrubException(throwable, 0);
    }

    private static Throwable scrubException(Throwable throwable, int depth) {
        if (throwable == null || depth > MAX_CAUSES) {
            return null;
        }
        String message = throwable.getClass().getName()
            + (carriesRowData(throwable) ? sqlState(throwable) : ": " + scrub(throwable.getMessage()));
        var copy = new ScrubbedException(message, scrubException(throwable.getCause(), depth + 1));
        copy.setStackTrace(throwable.getStackTrace());
        return copy;
    }

    /**
     * Database errors quote the failing row or key ({@code Failing row contains (...)}, {@code Key (email)=(...)}),
     * which may hold names, phones and addresses that {@link #scrub(String)} cannot detect: drop their message.
     */
    private static boolean carriesRowData(Throwable throwable) {
        String type = throwable.getClass().getName();
        return throwable instanceof SQLException
            || DATABASE_EXCEPTION_PACKAGES.stream().anyMatch(type::startsWith);
    }

    private static String sqlState(Throwable throwable) {
        return throwable instanceof SQLException sql ? " (SQLState " + sql.getSQLState() + ")" : "";
    }

    /** Replaces emails and national ids found inside free text. Addresses cannot be detected: never log them. */
    public static String scrub(String text) {
        if (text == null) {
            return null;
        }
        String result = EMAIL.matcher(text).replaceAll(MASK + "@$1");
        result = TAX_ID.matcher(result).replaceAll(MASK);
        return NATIONAL_ID.matcher(result).replaceAll(MASK);
    }
}
