package com.pijamaparty.store.payments.adapter.out.mercadopago;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MercadoPagoProperties.class)
class MercadoPagoConfiguration {

    /**
     * {@code store.payments.mercadopago.access-token}: the seller's access token (the Docker secret
     * {@code MERCADOPAGO_ACCESS_TOKEN}; test credentials give the sandbox). Empty: every checkout is unavailable,
     * without calling. Read with {@code @Value}, so an unresolved placeholder stops the start-up (the production
     * profile has no default) instead of becoming the token.
     *
     * <p>{@code store.public-url}: the storefront's origin, where MercadoPago sends the buyer back and notifies.
     */
    @Bean
    MercadoPagoGateway mercadoPagoGateway(MercadoPagoProperties properties,
                                          @Value("${store.payments.mercadopago.access-token}") String accessToken,
                                          @Value("${store.public-url}") URI publicUrl, MessageSource messages) {
        return new MercadoPagoGateway(properties, accessToken, publicUrl, messages);
    }
}
