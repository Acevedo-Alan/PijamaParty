package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the customer itself: the database shows a customer only its own addresses (V29). */
@Service
class ListAddressesService implements ListAddresses {

    private final LoadAddressBook books;

    ListAddressesService(LoadAddressBook books) {
        this.books = books;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Actor actor) {
        if (!actor.is(Role.CUSTOMER) || actor.userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        return new Result.Listed(books.findByCustomer(actor.userId().get()).addresses().stream()
            .map(AddressView::of)
            .toList());
    }
}
