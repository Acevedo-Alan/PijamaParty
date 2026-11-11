package com.pijamaparty.store;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against a throwaway PostgreSQL container: {@code ./mvnw spring-boot:test-run@dev}.
 * The container starts empty, so {@link DevCatalogSeed} fills it with a demo catalog and {@link DevCustomerSeed} adds a
 * verified customer to sign in with.
 */
public class TestStoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(StoreApplication::main)
            .with(TestcontainersConfiguration.class, DevCatalogSeed.class, DevCustomerSeed.class)
            .withAdditionalProfiles("dev")
            .run(args);
    }
}
