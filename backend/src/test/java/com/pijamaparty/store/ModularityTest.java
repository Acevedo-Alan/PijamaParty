package com.pijamaparty.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/** Fails on module cycles, on access to another module's internals and on dependencies not allowed by ARCHITECTURE.md. */
class ModularityTest {

    static final ApplicationModules modules = ApplicationModules.of(StoreApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }

    @Test
    void detectsTheModulesOfArchitectureSection3() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
            .containsExactlyInAnyOrder("catalog", "inventory", "cart", "promotions", "shipping", "ordering",
                "payments", "notifications", "accounts", "reviews", "activity", "backoffice", "platform");
    }
}
