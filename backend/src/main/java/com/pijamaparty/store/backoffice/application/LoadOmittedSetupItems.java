package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupItem;
import java.util.Set;

/** Outbound read port (UC-46): the setup guide's items the owner omitted. */
@FunctionalInterface
public interface LoadOmittedSetupItems {

    Set<SetupItem> omitted();
}
