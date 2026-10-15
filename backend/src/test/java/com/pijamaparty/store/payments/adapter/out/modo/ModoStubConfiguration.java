package com.pijamaparty.store.payments.adapter.out.modo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Switches MODO on and points the application's MODO gateway, payment lookups and webhook keys at a {@link ModoStub},
 * so whole-application tests pay with MODO without the network (T-E3b-03). Only the tests that need MODO import it;
 * they inject the stub and {@link ModoStub#reset()} it after.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ModoStubConfiguration {

    public static final String PROCESSOR_CODE = "P-TEST";

    @Bean(destroyMethod = "stop")
    ModoStub modoStub() {
        return new ModoStub();
    }

    @Bean
    DynamicPropertyRegistrar modoStubProperties(ModoStub stub) {
        return registry -> {
            registry.add("store.payments.modo.enabled", () -> "true");
            registry.add("store.payments.modo.base-url", () -> stub.baseUrl().toString());
            registry.add("store.payments.modo.username", () -> ModoStub.USERNAME);
            registry.add("store.payments.modo.password", () -> ModoStub.PASSWORD);
            registry.add("store.payments.modo.processor-code", () -> PROCESSOR_CODE);
        };
    }
}
