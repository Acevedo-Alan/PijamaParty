package com.pijamaparty.store.payments.adapter.out.modo;

import com.nimbusds.jose.jwk.JWKSet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UC-15 (T-E3b-03): the JWK set MODO signs its notifications with, from
 * {@code {base-url}/v2/payment-requests/.well-known/jwks.json}. MODO asks for it to be downloaded once, not on every
 * notification: it is downloaded on the first notification and again only when one is signed with a key the set does
 * not have, at most once every {@link #MIN_INTERVAL}, so forged notifications cannot make the store hammer MODO. Only
 * the public keys are kept. Its own JDK client with the RNF-21 timeouts and no redirects.
 */
public final class ModoJwks {

    static final String JWKS_PATH = "/v2/payment-requests/.well-known/jwks.json";
    public static final Duration MIN_INTERVAL = Duration.ofMinutes(5);

    /** A JWK set is a few kilobytes; a larger answer is not read. */
    private static final int MAX_SIZE = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ModoJwks.class);

    private final HttpClient http;
    private final URI uri;
    private final Duration readTimeout;
    private final String userAgent;
    private final Clock clock;

    private JWKSet keys = new JWKSet();
    private Instant lastDownload;

    public ModoJwks(URI baseUrl, Duration connectTimeout, Duration readTimeout, String userAgent, Clock clock) {
        this.http = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.uri = URI.create(baseUrl.toString().replaceAll("/+$", "") + JWKS_PATH);
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The keys downloaded, downloading them the first time. Empty when MODO never gave them. */
    public synchronized JWKSet current() {
        if (lastDownload == null) {
            download();
        }
        return keys;
    }

    /**
     * For a notification signed with a key the set does not have: the keys downloaded again, unless they were less
     * than {@link #MIN_INTERVAL} ago.
     */
    public synchronized JWKSet refreshed() {
        if (lastDownload == null || !clock.instant().isBefore(lastDownload.plus(MIN_INTERVAL))) {
            download();
        }
        return keys;
    }

    private void download() {
        lastDownload = clock.instant();
        HttpResponse<byte[]> response;
        try {
            response = http.send(HttpRequest.newBuilder(uri)
                .timeout(readTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException unreachable) {
            log.warn("MODO's JWK set could not be downloaded: {}", unreachable.getClass().getSimpleName());
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        if (response.statusCode() != 200 || response.body().length > MAX_SIZE) {
            log.warn("MODO's JWK set could not be downloaded: HTTP {}, {} bytes", response.statusCode(),
                response.body().length);
            return;
        }
        try {
            keys = JWKSet.parse(new String(response.body(), StandardCharsets.UTF_8)).toPublicJWKSet();
        } catch (ParseException unusable) {
            log.warn("MODO's JWK set could not be read");
        }
    }
}
