package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;

/**
 * UC-28 (T-E4-16): a module's part of the customer's data download. {@code accounts} depends on no module, so the
 * modules that hold a customer's data implement this port and {@code accounts} collects them. Joins the download's
 * read-only transaction, which runs as that customer (RNF-27), so Row Level Security shows only its own rows.
 */
public interface CustomerDataContributor {

    /** At most one contributor per section. */
    CustomerDataSection section();

    /**
     * The customer's records of this section, ready to be written as JSON. Personal data, the owner's own: never
     * logged (R-05).
     */
    List<?> export(UserId customer);
}
