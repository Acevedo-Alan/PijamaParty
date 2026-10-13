package com.pijamaparty.store.payments.adapter.out.mercadopago;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the application's MercadoPago gateway at a {@link MercadoPagoStub}, so whole-application tests open real
 * checkouts without the network. Tests that change the stub inject it and {@link MercadoPagoStub#reset()} it after.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MercadoPagoStubConfiguration {

    public static final String ACCESS_TOKEN = "TEST-access-token-for-the-stub";

    @Bean(destroyMethod = "stop")
    MercadoPagoStub mercadoPagoStub() {
        return new MercadoPagoStub();
    }

    @Bean
    DynamicPropertyRegistrar mercadoPagoStubProperties(MercadoPagoStub stub) {
        return registry -> {
            registry.add("store.payments.mercadopago.base-url", () -> stub.baseUrl().toString());
            registry.add("store.payments.mercadopago.access-token", () -> ACCESS_TOKEN);
        };
    }
}
