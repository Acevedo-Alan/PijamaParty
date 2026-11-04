package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Listing;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Outbound read port (UC-34): one page of the registered customers, as {@code accounts.api.CustomerDirectory} lists
 * them, in the caller's transaction.
 */
public interface RegisteredCustomers {

    Listing list(Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page);
}
