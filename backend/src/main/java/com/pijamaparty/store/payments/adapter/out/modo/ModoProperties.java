package com.pijamaparty.store.payments.adapter.out.modo;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MODO's payment button, API v2 (UC-14, T-E3b-02). The credentials are read apart, with {@code @Value}
 * ({@link ModoConfiguration}).
 *
 * @param enabled             whether buyers may choose MODO at the checkout; off by default, so the store can go live
 *                            with MercadoPago alone
 * @param requireCredentials  true in the production profile: enabled without credentials, the API does not start
 * @param baseUrl             the API, {@code https://merchants.playdigital.com.ar} (preproduction
 *                            {@code https://merchants.preprod.playdigital.com.ar}); tests point it at a local stub
 * @param checkoutUrl         MODO's payment site, {@code https://site.modo.com.ar} (preproduction
 *                            {@code https://site.preprod.modo.com.ar})
 * @param processorCode       the card gateway's code MODO gave the store; the only setting that depends on the gateway
 * @param ccCode              the installments plan, {@code 1CSI}: one payment, since MODO reports the amount with the
 *                            installments' interest (UC-15 would refuse it)
 * @param merchantName        the {@code User-Agent} of every request; MODO refuses requests without one
 * @param connectTimeout      RNF-21: 3 s
 * @param readTimeout         RNF-21: 10 s
 */
@ConfigurationProperties("store.payments.modo")
record ModoProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("false") boolean requireCredentials,
    @DefaultValue("https://merchants.playdigital.com.ar") URI baseUrl,
    @DefaultValue("https://site.modo.com.ar") URI checkoutUrl,
    @DefaultValue("") String processorCode,
    @DefaultValue("1CSI") String ccCode,
    @DefaultValue("PijamaParty") String merchantName,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {
}
