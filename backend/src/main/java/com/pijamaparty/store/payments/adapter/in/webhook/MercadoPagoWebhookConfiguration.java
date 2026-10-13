package com.pijamaparty.store.payments.adapter.in.webhook;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MercadoPagoWebhookConfiguration {

    /**
     * {@code store.payments.mercadopago.webhook-secret}: the secret key of the application's Webhooks in MercadoPago's
     * "Tus integraciones" (the Docker secret {@code MERCADOPAGO_WEBHOOK_SECRET}). Empty: every notification is
     * refused. Read with {@code @Value}, so an unresolved placeholder stops the start-up (the production profile has
     * no default).
     *
     * <p>{@code store.payments.mercadopago.webhook-max-age}: how old a signed notification may be. MercadoPago retries
     * for hours, and whether a retry keeps its {@code ts} is not documented.
     */
    @Bean
    MercadoPagoSignature mercadoPagoSignature(@Value("${store.payments.mercadopago.webhook-secret}") String secret,
                                              @Value("${store.payments.mercadopago.webhook-max-age:24h}")
                                              Duration maxAge, Clock clock) {
        return new MercadoPagoSignature(secret, maxAge, clock);
    }
}
