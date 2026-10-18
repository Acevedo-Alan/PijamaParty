package com.pijamaparty.store.reviews.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.UserNames;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.CustomerNames;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** {@link CustomerNames} through the public API of accounts, which shows a customer only its own account (V29). */
@Component
class AccountsCustomerNames implements CustomerNames {

    private final UserNames userNames;

    AccountsCustomerNames(UserNames userNames) {
        this.userNames = userNames;
    }

    @Override
    public Optional<String> nameOf(UserId customer) {
        return Optional.ofNullable(userNames.namesOf(Set.of(customer)).get(customer));
    }
}
