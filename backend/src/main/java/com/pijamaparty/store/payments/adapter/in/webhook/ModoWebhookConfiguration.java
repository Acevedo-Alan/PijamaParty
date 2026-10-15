package com.pijamaparty.store.payments.adapter.in.webhook;

import com.pijamaparty.store.payments.adapter.out.modo.ModoJwks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ModoWebhookConfiguration {

    /** Verified against MODO's JWK set, which the MODO adapter downloads from the API it is configured with. */
    @Bean
    ModoSignature modoSignature(ModoJwks jwks) {
        return new ModoSignature(jwks);
    }
}
