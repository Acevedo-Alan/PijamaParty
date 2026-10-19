package com.pijamaparty.store;

import org.springframework.boot.SpringApplication;

/**
 * T-E6-01: the backend of the end-to-end tests (e2e/), against a throwaway PostgreSQL container, with the {@code e2e}
 * profile of the test resources (payment and email providers pointed at the local stubs) and an adjustable clock:
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.pijamaparty.store.E2eStoreApplication}.
 * Playwright starts it (e2e/playwright.config.ts).
 */
public class E2eStoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(StoreApplication::main)
            .with(TestcontainersConfiguration.class, E2eConfiguration.class)
            .withAdditionalProfiles("dev", "e2e")
            .run(args);
    }
}
