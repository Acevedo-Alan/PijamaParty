package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.domain.StoreSettings;

/**
 * UC-36: the contact data, the social links and the announcement the storefront shows. Public and read only, the
 * same for every visitor; the adapter shows only those parts of the settings.
 */
public interface GetPublicStoreContact {

    StoreSettings execute();
}
