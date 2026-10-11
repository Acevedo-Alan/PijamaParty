package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.UserNames;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Names for other modules (UC-07), read in the caller's transaction and security context. */
@Service
class UserNamesService implements UserNames {

    private final LoadUser users;

    UserNamesService(LoadUser users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, String> namesOf(Collection<UserId> ids) {
        // A page of the stock history names one user or a few: one lookup each is enough.
        Map<UserId, String> names = new HashMap<>();
        for (UserId id : Set.copyOf(ids)) {
            users.findById(id).ifPresent(user -> names.put(id, user.name()));
        }
        return Map.copyOf(names);
    }
}
