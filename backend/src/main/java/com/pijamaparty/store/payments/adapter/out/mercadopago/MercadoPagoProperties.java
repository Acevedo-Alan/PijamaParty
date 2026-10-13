package com.pijamaparty.store.payments.adapter.out.mercadopago;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MercadoPago Checkout Pro (UC-14, T-E3-06).
 *
 * @param baseUrl        the API, {@code https://api.mercadopago.com}; tests point it at a local stub
 * @param connectTimeout RNF-21: 3 s
 * @param readTimeout    RNF-21: 10 s
 */
@ConfigurationProperties("store.payments.mercadopago")
record MercadoPagoProperties(
    @DefaultValue("https://api.mercadopago.com") URI baseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {
}
