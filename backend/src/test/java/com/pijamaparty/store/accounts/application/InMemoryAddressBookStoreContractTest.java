package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;

class InMemoryAddressBookStoreContractTest extends AddressBookStoreContractTest<InMemoryAddressBookStore> {

    private final InMemoryUserStore users = new InMemoryUserStore();

    @Override
    protected InMemoryAddressBookStore createSubject() {
        return new InMemoryAddressBookStore(users);
    }

    @Override
    protected void arrangeAccount(UserId customer) {
        users.save(User.newCustomer(customer, new Email(customer.value() + "@example.com"), "Ana",
            UserStoreContractTest.HASH, UserStoreContractTest.NOW));
    }
}
