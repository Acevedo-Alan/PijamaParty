package com.pijamaparty.store.ordering.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * UC-13: the buyer data and the address as typed in the checkout, before the rules of UC-13 normalize and validate
 * them (RNF-28). Personal data (R-05): {@code toString()} never prints it.
 *
 * @param floor null or blank for none
 * @param apartment null or blank for none
 */
public record CheckoutForm(String name, String email, String phone, String nationalId, String street, String number,
                           String floor, String apartment, String city, String postalCode) {

    // Printable ASCII without spaces, one '@' with text on both sides: lower case is then the same in Java and in
    // PostgreSQL, whose check compares with lower().
    private static final Pattern EMAIL = Pattern.compile("[\\x21-\\x3F\\x41-\\x7E]+@[\\x21-\\x3F\\x41-\\x7E]+");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s+\\-()]");
    private static final Pattern PHONE = Pattern.compile("[0-9]{8,15}");
    private static final Pattern NATIONAL_ID_SEPARATORS = Pattern.compile("[.\\s]");
    private static final Pattern NATIONAL_ID = Pattern.compile("[0-9]{7,8}");
    private static final Pattern POSTAL_CODE = Pattern.compile("[0-9]{4}|[A-Z][0-9]{4}[A-Z]{3}");

    @Override
    public String toString() {
        return "CheckoutForm[***]";
    }

    /** Every field that breaks a rule, or the normalized data when none does. */
    public Outcome validate() {
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        String trimmedName = trimmed(name);
        check(trimmedName, 1, 100, Field.NAME, invalid);
        String normalizedEmail = trimmed(email).toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254 || !EMAIL.matcher(normalizedEmail).matches()) {
            invalid.add(Field.EMAIL);
        }
        String digitsOfPhone = PHONE_SEPARATORS.matcher(Objects.requireNonNullElse(phone, "")).replaceAll("");
        if (!PHONE.matcher(digitsOfPhone).matches()) {
            invalid.add(Field.PHONE);
        }
        String digitsOfNationalId =
            NATIONAL_ID_SEPARATORS.matcher(Objects.requireNonNullElse(nationalId, "")).replaceAll("");
        if (!NATIONAL_ID.matcher(digitsOfNationalId).matches()) {
            invalid.add(Field.NATIONAL_ID);
        }
        String trimmedStreet = trimmed(street);
        check(trimmedStreet, 1, 100, Field.STREET, invalid);
        String trimmedNumber = trimmed(number);
        check(trimmedNumber, 1, 10, Field.NUMBER, invalid);
        Optional<String> optionalFloor = optional(floor);
        optionalFloor.ifPresent(value -> check(value, 1, 10, Field.FLOOR, invalid));
        Optional<String> optionalApartment = optional(apartment);
        optionalApartment.ifPresent(value -> check(value, 1, 10, Field.APARTMENT, invalid));
        String trimmedCity = trimmed(city);
        check(trimmedCity, 1, 80, Field.CITY, invalid);
        String normalizedPostalCode = trimmed(postalCode).toUpperCase(Locale.ROOT);
        if (!POSTAL_CODE.matcher(normalizedPostalCode).matches()) {
            invalid.add(Field.POSTAL_CODE);
        }
        if (!invalid.isEmpty()) {
            return new Outcome.Invalid(invalid);
        }
        return new Outcome.Valid(
            new BuyerDetails(trimmedName, normalizedEmail, digitsOfPhone, digitsOfNationalId),
            new ShippingAddress(trimmedStreet, trimmedNumber, optionalFloor, optionalApartment, trimmedCity,
                normalizedPostalCode));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.strip();
    }

    private static Optional<String> optional(String value) {
        return Optional.of(trimmed(value)).filter(text -> !text.isEmpty());
    }

    private static void check(String value, int min, int max, Field field, Set<Field> invalid) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            invalid.add(field);
        }
    }

    /** The fields of the form, as UC-13 names them. */
    public enum Field {
        NAME,
        EMAIL,
        PHONE,
        NATIONAL_ID,
        STREET,
        NUMBER,
        FLOOR,
        APARTMENT,
        CITY,
        POSTAL_CODE
    }

    public sealed interface Outcome {

        record Valid(BuyerDetails buyer, ShippingAddress address) implements Outcome {}

        /** At least one field, each named once. */
        record Invalid(Set<Field> fields) implements Outcome {

            public Invalid {
                fields = Set.copyOf(fields);
                if (fields.isEmpty()) {
                    throw new IllegalArgumentException("an invalid form names its invalid fields");
                }
            }
        }
    }
}
