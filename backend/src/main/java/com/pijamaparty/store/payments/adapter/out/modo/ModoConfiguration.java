package com.pijamaparty.store.payments.adapter.out.modo;

import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code store.payments.modo.username} and {@code password}: the store's MODO credentials (the Docker secrets
 * {@code MODO_USERNAME} and {@code MODO_PASSWORD}; MODO's generic preproduction credentials serve for development).
 * Without them, or without a processor code, every MODO checkout is unavailable, without calling. The gateway exists
 * even while MODO is switched off, so an order placed with MODO before still pays through it; enabled with
 * {@code require-credentials} (the production profile) and without them, the API does not start.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModoProperties.class)
class ModoConfiguration {

    @Bean
    ModoGateway modoGateway(ModoProperties properties, @Value("${store.payments.modo.username}") String username,
                            @Value("${store.payments.modo.password}") String password,
                            @Value("${store.public-url}") URI publicUrl, MessageSource messages, Clock clock) {
        boolean configured = configured(properties, username, password);
        if (properties.enabled() && properties.requireCredentials() && !configured) {
            throw new IllegalStateException("MODO is enabled (store.payments.modo.enabled) without its credentials "
                + "(MODO_USERNAME, MODO_PASSWORD) or its processor code (store.payments.modo.processor-code)");
        }
        var client = ModoRestClients.create(properties);
        return new ModoGateway(properties, client, new ModoAccessTokens(client, username, password, clock),
            configured, publicUrl, messages, clock);
    }

    /** Whether MODO can be called: credentials and a processor code. */
    static boolean configured(ModoProperties properties, String username, String password) {
        return !username.isBlank() && !password.isBlank() && !properties.processorCode().isBlank();
    }
}
