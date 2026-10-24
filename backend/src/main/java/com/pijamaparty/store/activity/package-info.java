/**
 * UC-37 (T-E8-01): the activity log of the back office. Depends on nothing, so every module that changes something
 * from the back office records it through {@code activity.api} without a cycle.
 */
@ApplicationModule(allowedDependencies = {"platform"})
package com.pijamaparty.store.activity;

import org.springframework.modulith.ApplicationModule;
