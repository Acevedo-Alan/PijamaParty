package com.pijamaparty.store.ordering.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-13: where the order goes, as the order keeps it (R-04), already normalized by {@link CheckoutForm}. The province
 * travels apart, as the shipping module's jurisdiction. Personal data (R-05): {@code toString()} never prints it.
 *
 * @param postalCode 4 digits or an Argentine CPA, in upper case
 */
public record ShippingAddress(String street, String number, Optional<String> floor, Optional<String> apartment,
                              String city, String postalCode) {

    public ShippingAddress {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(apartment, "apartment");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(postalCode, "postalCode");
    }

    @Override
    public String toString() {
        return "ShippingAddress[***]";
    }
}
