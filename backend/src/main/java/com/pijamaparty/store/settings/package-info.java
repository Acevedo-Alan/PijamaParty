/**
 * UC-36 (T-E8-04): the store's settings, edited in "Datos de la tienda": contact data, social links, the header
 * announcement and the low-stock threshold. The activity log of UC-37 is its only dependency.
 */
@ApplicationModule(allowedDependencies = {"platform", "activity::api"})
package com.pijamaparty.store.settings;

import org.springframework.modulith.ApplicationModule;
