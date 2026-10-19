package com.pijamaparty.store.accounts.adapter.in.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Enables Google and Facebook sign-in against a {@link ProvidersStub}, so whole-application tests sign in with a
 * provider without the network. Tests inject the stub and {@link ProvidersStub#reset()} it after.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ProvidersStubConfiguration {

    @Bean(destroyMethod = "stop")
    ProvidersStub providersStub() {
        return new ProvidersStub();
    }

    /** Idle unless a test arms it: the whole-application tests of sign-in share this context (T-E6-03). */
    @Bean
    FailingSignInListener failingSignInListener() {
        return new FailingSignInListener();
    }

    @Bean
    DynamicPropertyRegistrar providersStubProperties(ProvidersStub stub) {
        return registry -> {
            registry.add("store.accounts.oauth2.google.client-id", () -> ProvidersStub.GOOGLE_CLIENT_ID);
            registry.add("store.accounts.oauth2.google.client-secret", () -> "google-secret-for-tests");
            registry.add("store.accounts.oauth2.google.token-uri", () -> stub.baseUrl() + "/google/token");
            registry.add("store.accounts.oauth2.google.jwk-set-uri", () -> stub.baseUrl() + "/google/certs");
            registry.add("store.accounts.oauth2.facebook.client-id", () -> ProvidersStub.FACEBOOK_CLIENT_ID);
            registry.add("store.accounts.oauth2.facebook.client-secret", () -> "facebook-secret-for-tests");
            registry.add("store.accounts.oauth2.facebook.graph-version", () -> ProvidersStub.GRAPH_VERSION);
            registry.add("store.accounts.oauth2.facebook.graph-url", () -> stub.baseUrl() + "/facebook");
        };
    }
}
