package com.pijamaparty.store.platform.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The only place that reads the system clock. Everything else receives this {@link Clock} by constructor (R-07). */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    /** For annotations that need a constant, such as the zone of a {@code @Scheduled} cron. */
    public static final String STORE_ZONE_ID = "America/Argentina/Buenos_Aires";

    public static final ZoneId STORE_ZONE = ZoneId.of(STORE_ZONE_ID);

    @Bean
    Clock clock() {
        return Clock.system(STORE_ZONE);
    }
}
