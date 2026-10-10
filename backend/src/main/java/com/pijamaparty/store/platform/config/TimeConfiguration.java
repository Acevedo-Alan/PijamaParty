package com.pijamaparty.store.platform.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The only place that reads the system clock. Everything else receives this {@link Clock} by constructor (R-07). */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    public static final ZoneId STORE_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    Clock clock() {
        return Clock.system(STORE_ZONE);
    }
}
