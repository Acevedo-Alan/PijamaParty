package com.pijamaparty.store.payments.adapter.out.modo;

import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client of the MODO API: its own JDK client with the RNF-21 timeouts, no redirects, no retries, and the
 * merchant's name as {@code User-Agent}, which MODO requires.
 */
final class ModoRestClients {

    private ModoRestClients() {
    }

    static RestClient create(ModoProperties properties) {
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.baseUrl().toString())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.merchantName())
            .build();
    }
}
