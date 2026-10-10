package com.pijamaparty.store.platform.web;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only routes: one public, one protected, to exercise problem+json and the security baseline. */
@TestConfiguration(proxyBeanMethods = false)
@Import(ProblemDetailsTestRoutes.TestController.class)
public class ProblemDetailsTestRoutes {

    @Bean
    HttpAuthorizationRules publicTestRoutes() {
        return registry -> registry.requestMatchers("/api/test/public/**").permitAll();
    }

    @RestController
    @RequestMapping("/api/test")
    static class TestController {

        record Greeting(@NotBlank @Size(max = 20) String name) {
        }

        @PostMapping("/public/greetings")
        Greeting greet(@Valid @RequestBody Greeting greeting) {
            return greeting;
        }

        @GetMapping("/public/failure")
        String fail() {
            throw new IllegalStateException("insert failed for ana@gmail.com dni 30123456 select * from app_user");
        }

        @GetMapping("/private/data")
        String privateData() {
            return "secret";
        }

        @GetMapping("/public/denied")
        String denied() {
            throw new org.springframework.security.access.AccessDeniedException("not yours");
        }
    }
}
