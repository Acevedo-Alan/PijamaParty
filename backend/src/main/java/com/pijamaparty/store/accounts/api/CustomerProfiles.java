package com.pijamaparty.store.accounts.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-34 (T-E8-11): a registered customer as the back office's profile shows it. Joins the caller's transaction, so its
 * security context decides what is visible (RNF-27): the administrator sees the customers, their addresses and the
 * providers of their linked accounts (V29, V44), never another administrator. Personal data: never logged.
 */
public interface CustomerProfiles {

    /** Empty when there is no customer with that id: unknown, deleted (UC-28), or not a customer. */
    Optional<CustomerProfile> find(UUID customerId);

    /** The provider of a linked sign-in account (UC-27); never its external subject or email. */
    enum SignInProvider {
        GOOGLE,
        FACEBOOK
    }

    /**
     * Personal data (R-05): {@link #toString()} prints only the id.
     *
     * @param phone digits only, as the customer saved it (UC-19)
     * @param providers in the order they were linked
     * @param addresses the main one first, then in the order they were added
     */
    record CustomerProfile(UUID id, String name, String email, boolean emailVerified, Optional<String> phone,
                           Instant registeredAt, List<SignInProvider> providers, List<SavedAddress> addresses) {

        public CustomerProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(phone, "phone");
            Objects.requireNonNull(registeredAt, "registeredAt");
            providers = List.copyOf(providers);
            addresses = List.copyOf(addresses);
        }

        @Override
        public String toString() {
            return "CustomerProfile[id=" + id + ", ***]";
        }
    }

    /**
     * An address of the customer's book (UC-20). Personal data (R-05): {@link #toString()} prints nothing of it.
     *
     * @param province the constant of the 24 jurisdictions, e.g. {@code SANTA_FE}
     */
    record SavedAddress(UUID id, String street, String number, Optional<String> floor, Optional<String> apartment,
                        String city, String province, String postalCode, boolean primary) {

        public SavedAddress {
            Objects.requireNonNull(id, "id");
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
            return "SavedAddress[id=" + id + ", primary=" + primary + ", ***]";
        }
    }
}
