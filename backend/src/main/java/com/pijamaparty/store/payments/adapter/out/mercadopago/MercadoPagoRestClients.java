package com.pijamaparty.store.payments.adapter.out.mercadopago;

import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client of the MercadoPago API: its own JDK client with the RNF-21 timeouts, no redirects, no retries. */
final class MercadoPagoRestClients {

    private MercadoPagoRestClients() {
    }

    static RestClient create(MercadoPagoProperties properties, String accessToken) {
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.baseUrl().toString())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .build();
    }
}
