package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/**
 * Outbound port: reads a customer's address book (UC-20) within the caller's transaction, as that customer: Row Level
 * Security shows it only its own addresses (V29).
 */
public interface LoadAddressBook {

    /** The customer's addresses; an empty book when it has none. */
    AddressBook findByCustomer(UserId customer);

    /**
     * For a change: first locks the customer's account row until the transaction ends ({@code SELECT ... FOR
     * UPDATE}), so two changes of one book run one after the other and each sees the other's addresses.
     *
     * @return empty when the account does not exist
     */
    Optional<AddressBook> lockByCustomer(UserId customer);
}
