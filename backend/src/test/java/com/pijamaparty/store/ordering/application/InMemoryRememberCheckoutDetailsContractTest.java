package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class InMemoryRememberCheckoutDetailsContractTest
    extends RememberCheckoutDetailsContractTest<InMemoryRememberCheckoutDetails> {

    @Override
    protected InMemoryRememberCheckoutDetails createSubject() {
        return new InMemoryRememberCheckoutDetails();
    }

    @Override
    protected UserId givenCustomer() {
        UserId customer = UserId.of(UUID.randomUUID());
        subject.customer(customer);
        return customer;
    }

    @Override
    protected void givenFullAddressBook(UserId customer) {
        for (int i = 0; i < 10; i++) {
            subject.address(customer, new RememberCheckoutDetails.AddressToSave(new ShippingAddress("Calle " + i,
                "100", Optional.empty(), Optional.empty(), "Santa Fe", "3000"), Province.SANTA_FE));
        }
    }

    @Override
    protected Optional<String> phoneOf(UserId customer) {
        return subject.phoneOf(customer);
    }

    @Override
    protected List<String> streetsOf(UserId customer) {
        return subject.addressesOf(customer).stream().map(each -> each.address().street()).toList();
    }
}
