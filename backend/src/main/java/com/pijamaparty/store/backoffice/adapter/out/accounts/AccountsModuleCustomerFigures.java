package com.pijamaparty.store.backoffice.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.CustomerCounts;
import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import com.pijamaparty.store.backoffice.application.CustomerFigures;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Counts the customers through the public API of the accounts module, in the caller's transaction. */
@Component
class AccountsModuleCustomerFigures implements CustomerFigures {

    private final CustomerCounts counts;

    AccountsModuleCustomerFigures(CustomerCounts counts) {
        this.counts = counts;
    }

    @Override
    public Counts count(Instant since) {
        return counts.count(since);
    }
}
