package com.pijamaparty.store.inventory.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.UserNames;
import com.pijamaparty.store.inventory.application.UserNameLookup;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Names of users through the accounts module's public API, in the caller's transaction. */
@Component
class AccountsUserNameLookup implements UserNameLookup {

    private final UserNames userNames;

    AccountsUserNameLookup(UserNames userNames) {
        this.userNames = userNames;
    }

    @Override
    public Map<UserId, String> namesOf(Set<UserId> ids) {
        return userNames.namesOf(ids);
    }
}
