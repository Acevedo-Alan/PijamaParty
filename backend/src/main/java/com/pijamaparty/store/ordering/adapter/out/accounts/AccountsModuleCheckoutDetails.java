package com.pijamaparty.store.ordering.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.CheckoutDetails;
import com.pijamaparty.store.ordering.application.RememberCheckoutDetails;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Leaves the checkout's details on the customer's account through the public API of the accounts module. */
@Component
class AccountsModuleCheckoutDetails implements RememberCheckoutDetails {

    private final CheckoutDetails checkoutDetails;

    AccountsModuleCheckoutDetails(CheckoutDetails checkoutDetails) {
        this.checkoutDetails = checkoutDetails;
    }

    @Override
    public void remember(UserId customer, String phone, Optional<AddressToSave> address) {
        checkoutDetails.remember(customer, phone, address.map(AccountsModuleCheckoutDetails::toAccounts));
    }

    private static CheckoutDetails.Address toAccounts(AddressToSave toSave) {
        ShippingAddress address = toSave.address();
        return new CheckoutDetails.Address(address.street(), address.number(), address.floor(), address.apartment(),
            address.city(), toSave.province().name(), address.postalCode());
    }
}
