package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupItem;
import java.time.Instant;

/** Outbound write port (UC-46): omitting an item of the setup guide, or showing it again. Both are idempotent. */
public interface SaveOmittedSetupItems {

    /** Keeps the item omitted; an item already omitted keeps its first time. Only an omittable item. */
    void omit(SetupItem item, Instant at);

    /** Shows the item again; nothing happens if it was not omitted. */
    void show(SetupItem item);
}
