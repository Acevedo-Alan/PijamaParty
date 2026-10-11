package com.pijamaparty.store.platform.web.ratelimit;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The test route of T-E0-12. Its policy is set by each test through properties, as real endpoints will be:
 * {@code limited} allows 3 requests per minute per IP, and a catch-all policy on {@code /api/**} allows 5, which
 * shows that excluded paths (health) are never limited.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(RateLimitTestRoutes.Controller.class)
public class RateLimitTestRoutes {

    public static final String[] POLICIES = {
        "store.rate-limit.enabled=true",
        "store.rate-limit.policies[0].name=limited",
        "store.rate-limit.policies[0].paths[0]=/api/test/public/limited",
        "store.rate-limit.policies[0].keys[0]=ip",
        "store.rate-limit.policies[0].capacity=3",
        "store.rate-limit.policies[0].period=1m",
        "store.rate-limit.policies[1].name=catch-all",
        "store.rate-limit.policies[1].paths[0]=/api/**",
        "store.rate-limit.policies[1].keys[0]=ip",
        "store.rate-limit.policies[1].capacity=5",
        "store.rate-limit.policies[1].period=1m",
    };

    @Bean
    HttpAuthorizationRules publicRateLimitTestRoute() {
        return registry -> registry.requestMatchers("/api/test/public/limited").permitAll();
    }

    @RestController
    static class Controller {

        @GetMapping("/api/test/public/limited")
        String limited() {
            return "ok";
        }
    }
}
