package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import java.util.List;

/**
 * Outbound read port of the back-office customer list (UC-34): the users with role {@code CUSTOMER}, newest first.
 * Within the caller's transaction, whose security context decides which users are visible.
 */
public interface CustomerPages {

    /** Those the filter lets through, skipping {@code offset} and returning at most {@code limit}. */
    List<Customer> page(CustomerFilter filter, int offset, int limit);
}
