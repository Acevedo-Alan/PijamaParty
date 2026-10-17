package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-21 (T-E4-06): what a customer's purchase leaves on its account for the next checkout. Joins the checkout's
 * transaction, which runs as that customer (RNF-27), so Row Level Security lets it change only its own account and
 * address book.
 */
public interface CheckoutDetails {

    /**
     * Locks the customer's account row, as every change of the address book does (UC-20), and sets its phone. With an
     * {@code address}, adds it to the book with the rules of UC-20: never the primary unless it is the first, and
     * nothing when the book is full. An account that no longer exists is left alone; the order is never refused.
     *
     * @param phone the order's phone, already normalized: 8 to 15 digits
     */
    void remember(UserId customer, String phone, Optional<Address> address);

    /**
     * The order's address, already normalized by the rules of UC-13. Personal data (R-05): {@code toString()} never
     * prints it.
     *
     * @param province the constant of {@code shipping.api.Province}, e.g. {@code SANTA_FE}
     */
    record Address(String street, String number, Optional<String> floor, Optional<String> apartment, String city,
                   String province, String postalCode) {

        public Address {
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
            return "Address[***]";
        }
    }
}
