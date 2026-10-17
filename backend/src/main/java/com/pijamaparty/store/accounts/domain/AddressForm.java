package com.pijamaparty.store.accounts.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * UC-20: an address as typed, before the rules of the order's address (UC-13, {@code ordering.domain.CheckoutForm})
 * normalize and validate it (RNF-28). Personal data (R-05): {@code toString()} never prints it.
 *
 * @param floor null or blank for none
 * @param apartment null or blank for none
 */
public record AddressForm(String street, String number, String floor, String apartment, String city,
                          Province province, String postalCode) {

    private static final Pattern POSTAL_CODE = Pattern.compile("[0-9]{4}|[A-Z][0-9]{4}[A-Z]{3}");

    public AddressForm {
        Objects.requireNonNull(province, "province");
    }

    @Override
    public String toString() {
        return "AddressForm[***]";
    }

    /** Every field that breaks a rule, or the normalized address when none does. */
    public Outcome validate() {
        Set<Field> invalid = EnumSet.noneOf(Field.class);
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
        return new Outcome.Valid(new AddressDetails(trimmedStreet, trimmedNumber, optionalFloor, optionalApartment,
            trimmedCity, province, normalizedPostalCode));
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

    /** The fields with a rule; the province is one of the enum or the request does not get here. */
    public enum Field {
        STREET,
        NUMBER,
        FLOOR,
        APARTMENT,
        CITY,
        POSTAL_CODE
    }

    public sealed interface Outcome {

        record Valid(AddressDetails address) implements Outcome {}

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
