package com.pijamaparty.store.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs live in each module's {@code adapter.in.scheduler} and run their use case as {@code SYSTEM}
 * ({@code CurrentActor.callAsSystem}). One instance of the API runs, so no job needs a distributed lock.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {
}
