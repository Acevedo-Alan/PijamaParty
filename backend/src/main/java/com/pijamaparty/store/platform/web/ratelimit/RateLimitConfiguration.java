package com.pijamaparty.store.platform.web.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import java.util.List;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

    @Bean
    RateLimiter rateLimiter(RateLimitProperties properties, List<RateLimitKeyResolver> resolvers) {
        return new RateLimiter(properties, resolvers, Ticker.systemTicker());
    }

    /** Runs before Spring Security, so abusive traffic is cut before any authentication work. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper) {
        var registration = new FilterRegistrationBean<>(new RateLimitFilter(limiter, objectMapper));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
