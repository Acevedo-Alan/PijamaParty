package com.pijamaparty.store.notifications.adapter.out.resend;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Resend, the transactional email service (RNF-23, T-E3-09).
 *
 * @param baseUrl        the API, {@code https://api.resend.com}; tests point it at a local stub
 * @param connectTimeout RNF-21: 3 s
 * @param readTimeout    RNF-21: 10 s
 */
@ConfigurationProperties("store.notifications.resend")
record ResendProperties(
    @DefaultValue("https://api.resend.com") URI baseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {
}
