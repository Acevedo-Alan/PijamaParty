/**
 * Shared kernel: Money, Clock wiring, typed ids, Actor, idempotency, rate limiting, problem+json and the
 * security context for Row Level Security. Open module: every module may use all of its packages.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, allowedDependencies = {})
package com.pijamaparty.store.platform;

import org.springframework.modulith.ApplicationModule;
