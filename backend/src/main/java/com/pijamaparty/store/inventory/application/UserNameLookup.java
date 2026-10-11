package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Map;
import java.util.Set;

/** Outbound port: the names of the users behind stock movements (UC-07), within the caller's transaction. */
public interface UserNameLookup {

    /** The name of each user that exists and that the caller may see; the others are left out. */
    Map<UserId, String> namesOf(Set<UserId> ids);
}
