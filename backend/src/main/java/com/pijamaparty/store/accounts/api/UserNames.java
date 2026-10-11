package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Collection;
import java.util.Map;

/**
 * The names of users, for screens that show who did something (the stock history of UC-07). Joins the caller's
 * transaction, so its security context decides which users are visible (RNF-27): the administrator sees its own.
 */
public interface UserNames {

    /** The name of each visible user among {@code ids}; unknown and hidden users are left out. */
    Map<UserId, String> namesOf(Collection<UserId> ids);
}
