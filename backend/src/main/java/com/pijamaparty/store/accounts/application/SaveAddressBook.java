package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressBook;

/** Outbound port: writes a customer's address book (UC-20) within the caller's transaction. */
public interface SaveAddressBook {

    /** The customer's addresses become exactly those of {@code book}. */
    void save(AddressBook book);
}
