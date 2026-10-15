package com.pijamaparty.store.notifications.adapter.out.resend;

import com.pijamaparty.store.notifications.adapter.in.webhook.ResendWebhooks;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the application's email sender at a {@link ResendStub}, so whole-application tests send real emails without
 * the network. Tests that send or change the stub inject it and {@link ResendStub#reset()} it after.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ResendStubConfiguration {

    public static final String API_KEY = "re_test_api_key_for_the_stub";

    @Bean(destroyMethod = "stop")
    ResendStub resendStub() {
        return new ResendStub();
    }

    @Bean
    DynamicPropertyRegistrar resendStubProperties(ResendStub stub) {
        return registry -> {
            registry.add("store.notifications.resend.base-url", () -> stub.baseUrl().toString());
            registry.add("store.notifications.resend.api-key", () -> API_KEY);
            // T-E3-22: tests sign the bounce notifications with it (ResendWebhooks)
            registry.add("store.notifications.resend.webhook-secret", () -> ResendWebhooks.SECRET);
        };
    }
}
