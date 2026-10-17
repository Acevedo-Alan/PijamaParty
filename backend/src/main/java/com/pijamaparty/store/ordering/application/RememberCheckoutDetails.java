package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound port: leaves a customer's checkout details on its account for the next checkout (UC-21, T-E4-06), within
 * the caller's transaction and as that customer. It never refuses the order.
 */
@FunctionalInterface
public interface RememberCheckoutDetails {

    /**
     * @param phone   the order's phone, normalized, which becomes the account's
     * @param address the order's address to add to the customer's address book; empty to add none
     */
    void remember(UserId customer, String phone, Optional<AddressToSave> address);

    /** The order's address and its province. Personal data (R-05): {@code toString()} never prints it. */
    record AddressToSave(ShippingAddress address, Province province) {

        public AddressToSave {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(province, "province");
        }

        @Override
        public String toString() {
            return "AddressToSave[***]";
        }
    }
}
