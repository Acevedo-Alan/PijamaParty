package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerCounts;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-33 (T-E8-14): {@link CustomerCounts} for the back office's summary, read as the caller (the administrator). */
@Service
class CustomerCountsService implements CustomerCounts {

    private final CountCustomers customers;

    CustomerCountsService(CountCustomers customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts count(Instant since) {
        return customers.count(since);
    }
}
