package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Address;
import com.pijamaparty.store.accounts.domain.AddressDetails;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import java.util.Optional;

/** An address of the address book as the customer sees it (UC-20). Only sent to the customer itself. */
public record AddressView(AddressId id, String street, String number, Optional<String> floor,
                          Optional<String> apartment, String city, Province province, String postalCode,
                          boolean primary) {

    static AddressView of(Address address) {
        AddressDetails details = address.details();
        return new AddressView(address.id(), details.street(), details.number(), details.floor(), details.apartment(),
            details.city(), details.province(), details.postalCode(), address.primary());
    }

    @Override
    public String toString() {
        return "AddressView[id=" + id + ", ***]";
    }
}
