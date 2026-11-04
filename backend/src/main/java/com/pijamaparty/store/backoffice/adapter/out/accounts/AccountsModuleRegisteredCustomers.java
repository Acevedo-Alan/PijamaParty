package com.pijamaparty.store.backoffice.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.CustomerDirectory;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Listing;
import com.pijamaparty.store.backoffice.application.RegisteredCustomers;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Reads the customers through the public API of the accounts module, in the caller's transaction. */
@Component
class AccountsModuleRegisteredCustomers implements RegisteredCustomers {

    private final CustomerDirectory directory;

    AccountsModuleRegisteredCustomers(CustomerDirectory directory) {
        this.directory = directory;
    }

    @Override
    public Listing list(Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page) {
        return directory.list(search, from, to, page);
    }
}
