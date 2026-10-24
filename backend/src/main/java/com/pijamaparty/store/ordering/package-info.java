@ApplicationModule(allowedDependencies = {"platform", "cart::api", "inventory::api", "promotions::api", "shipping::api", "payments::api",
    "accounts::api", "activity::api"})
package com.pijamaparty.store.ordering;

import org.springframework.modulith.ApplicationModule;
