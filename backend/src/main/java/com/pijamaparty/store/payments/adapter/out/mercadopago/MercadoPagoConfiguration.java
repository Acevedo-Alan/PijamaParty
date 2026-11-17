package com.pijamaparty.store.payments.adapter.out.mercadopago;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code store.payments.mercadopago.access-token}: the seller's access token (the Docker secret
 * {@code MERCADOPAGO_ACCESS_TOKEN}; test credentials give the sandbox). Empty: every card charge and every payment
 * lookup is unavailable, without calling. Read with {@code @Value}, so an unresolved placeholder stops the start-up (the
 * production profile has no default) instead of becoming the token.
 *
 * <p>{@code store.payments.mercadopago.public-key}: the public key of the same account, for the browser's card form
 * (T-E3-29; the Docker secret {@code MERCADOPAGO_PUBLIC_KEY}). Empty: the card form cannot open.
 *
 * <p>MercadoPago notifies the URL of the application's Webhooks, set in its panel (UC-15, T-E3-34): nothing here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MercadoPagoProperties.class)
class MercadoPagoConfiguration {

    /** T-E3-29: the cards typed on the store's page, charged with the access token. */
    @Bean
    MercadoPagoCardCharges mercadoPagoCardCharges(
        MercadoPagoProperties properties, @Value("${store.payments.mercadopago.access-token}") String accessToken,
        @Value("${store.payments.mercadopago.public-key}") String publicKey, MessageSource messages) {
        return new MercadoPagoCardCharges(properties, accessToken, publicKey, messages);
    }

    /** UC-15: the orders MercadoPago notifies are asked to it with the same access token. */
    @Bean
    MercadoPagoPaymentVerifier mercadoPagoPaymentVerifier(
        MercadoPagoProperties properties, @Value("${store.payments.mercadopago.access-token}") String accessToken) {
        return new MercadoPagoPaymentVerifier(properties, accessToken);
    }
}
