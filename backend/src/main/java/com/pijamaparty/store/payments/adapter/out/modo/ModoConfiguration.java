package com.pijamaparty.store.payments.adapter.out.modo;

import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@code store.payments.modo.username} and {@code password}: the store's MODO credentials (the Docker secrets
 * {@code MODO_USERNAME} and {@code MODO_PASSWORD}; MODO's generic preproduction credentials serve for development).
 * Without them, or without a processor code, every MODO checkout and payment lookup is unavailable, without calling.
 * The gateway and the verifier exist even while MODO is switched off, so an order placed with MODO before still pays
 * through it; enabled with {@code require-credentials} (the production profile) and without them, the API does not
 * start. The gateway and the verifier share one client and one access token (T-E3b-03).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModoProperties.class)
class ModoConfiguration {

    @Bean
    ModoClient modoClient(ModoProperties properties, @Value("${store.payments.modo.username}") String username,
                          @Value("${store.payments.modo.password}") String password, Clock clock) {
        boolean configured = configured(properties, username, password);
        if (properties.enabled() && properties.requireCredentials() && !configured) {
            throw new IllegalStateException("MODO is enabled (store.payments.modo.enabled) without its credentials "
                + "(MODO_USERNAME, MODO_PASSWORD) or its processor code (store.payments.modo.processor-code)");
        }
        var client = ModoRestClients.create(properties);
        return new ModoClient(client, new ModoAccessTokens(client, username, password, clock), configured);
    }

    @Bean
    ModoGateway modoGateway(ModoProperties properties, ModoClient modo, @Value("${store.public-url}") URI publicUrl,
                            MessageSource messages, Clock clock) {
        return new ModoGateway(properties, modo.client(), modo.tokens(), modo.configured(), publicUrl, messages,
            clock);
    }

    /**
     * MODO's JWK set lives on its API ({@code base-url}), so preproduction and production each verify
     * notifications with their own keys; downloaded with the API's timeouts and the merchant's name as {@code User-Agent} (T-E3b-03).
     */
    @Bean
    ModoJwks modoJwks(ModoProperties properties, Clock clock) {
        return new ModoJwks(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(),
            properties.merchantName(), clock);
    }

    @Bean
    ModoPaymentVerifier modoPaymentVerifier(ModoClient modo) {
        return new ModoPaymentVerifier(modo.client(), modo.tokens(), modo.configured());
    }

    /** Whether MODO can be called: credentials and a processor code. */
    static boolean configured(ModoProperties properties, String username, String password) {
        return !username.isBlank() && !password.isBlank() && !properties.processorCode().isBlank();
    }

    /** What the gateway and the verifier share. */
    record ModoClient(RestClient client, ModoAccessTokens tokens, boolean configured) {}
}
