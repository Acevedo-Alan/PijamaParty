package com.pijamaparty.store.accounts.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-20: an address of the address book, already normalized by {@link AddressForm}. Personal data (R-05):
 * {@code toString()} never prints it.
 *
 * @param postalCode 4 digits or an Argentine CPA, in upper case
 */
public record AddressDetails(String street, String number, Optional<String> floor, Optional<String> apartment,
                             String city, Province province, String postalCode) {

    public AddressDetails {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(apartment, "apartment");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(province, "province");
        Objects.requireNonNull(postalCode, "postalCode");
    }

    @Override
    public String toString() {
        return "AddressDetails[***]";
    }
}
