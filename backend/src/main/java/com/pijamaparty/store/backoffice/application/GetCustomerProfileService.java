package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-34 (T-E8-11): the account from {@code accounts} with the orders from {@code ordering}, composed here because
 * {@code ordering} depends on {@code accounts} (ARCHITECTURE.md section 3, rule 3). One transaction, which also
 * records the view (UC-37): never the customer's name or email, only its id.
 */
@Service
class GetCustomerProfileService implements GetCustomerProfile {

    private final CustomerAccounts accounts;
    private final CustomerOrderHistories orders;
    private final BackofficeActivity activity;

    GetCustomerProfileService(CustomerAccounts accounts, CustomerOrderHistories orders, BackofficeActivity activity) {
        this.accounts = accounts;
        this.orders = orders;
        this.activity = activity;
    }

    @Override
    @Transactional
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN) || query.actor().userId().isEmpty()) {
            return new Result.Forbidden();
        }
        Optional<CustomerProfile> profile = accounts.find(query.customerId());
        if (profile.isEmpty()) {
            return new Result.NotFound();
        }
        var shown = new Result.Shown(profile.get(), orders.historyOf(query.customerId(), query.ordersPage()));
        if (query.ordersPage() == 0) {
            activity.record(new ActivityEntry(query.actor().userId().get(), ActivityAction.CUSTOMER_VIEWED,
                ActivitySubject.of(SubjectType.CUSTOMER, query.customerId())));
        }
        return shown;
    }
}
