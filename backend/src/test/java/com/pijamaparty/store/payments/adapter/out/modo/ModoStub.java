package com.pijamaparty.store.payments.adapter.out.modo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The MODO API on a free local port (JDK HTTP server, no network). {@code POST /v2/stores/companies/token} gives a new
 * token per request, valid a week, and {@code POST /v2/payment-requests/} creates a payment request with a new id
 * per call, for a token it gave and has not {@link #revokeTokens() revoked} (otherwise {@code 401}).
 * {@code GET /v2/payment-requests/{id}/data} answers the payments it {@link #holdsPayment holds} as MODO documents
 * them, and {@code 404} for the rest (T-E3b-03). {@code GET /v2/payment-requests/.well-known/jwks.json} publishes the
 * public key it signs its {@link #notification notifications} with. Tests can make any of it fail or slow down.
 */
public final class ModoStub implements AutoCloseable {

    static final String USERNAME = "test-modo-user";
    static final String PASSWORD = "test-modo-password";
    static final long TOKEN_LIFETIME_SECONDS = 604_800;

    public static final String JWKS_PATH = "/v2/payment-requests/.well-known/jwks.json";
    private static final Pattern DATA_PATH = Pattern.compile("/v2/payment-requests/([^/]+)/data");

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Generated once: every stub starts signing with it. */
    private static final RSAKey FIRST_KEY = newKey("modo-key-0");

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
    private final Map<String, String> payments = new ConcurrentHashMap<>();
    private final AtomicInteger tokenSequence = new AtomicInteger();
    private final AtomicInteger requestSequence = new AtomicInteger();
    private final AtomicInteger keySequence = new AtomicInteger();
    private volatile Integer paymentRequestStatus;
    private volatile String paymentRequestBody;
    private volatile Integer tokenStatus;
    private volatile String tokenBody;
    private volatile Integer dataStatus;
    private volatile String dataBody;
    private volatile Duration delay = Duration.ZERO;
    private volatile RSAKey signingKey = FIRST_KEY;
    private volatile boolean stopped;

    /** What the stub received. */
    public record Request(String method, String path, String authorization, String userAgent, String body) {

        JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public ModoStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(new Request(exchange.getRequestMethod(), path, authorization,
            exchange.getRequestHeaders().getFirst("User-Agent"), received));
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Matcher data = DATA_PATH.matcher(path);
        int status;
        String body;
        if (JWKS_PATH.equals(path)) {
            status = 200;
            body = new JWKSet(signingKey.toPublicJWK()).toString();
        } else if (ModoAccessTokens.TOKEN_PATH.equals(path)) {
            if (tokenStatus != null) {
                status = tokenStatus;
                body = tokenBody;
            } else if (received.contains("\"" + USERNAME + "\"") && received.contains("\"" + PASSWORD + "\"")) {
                String token = "token-" + tokenSequence.incrementAndGet();
                validTokens.add(token);
                status = 200;
                body = "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                    + TOKEN_LIFETIME_SECONDS + "}";
            } else {
                status = 401;
                body = "{\"message\":\"invalid credentials\"}";
            }
        } else if (authorization == null || !validTokens.contains(authorization.replaceFirst("^Bearer ", ""))) {
            status = 401;
            body = "{\"message\":\"unauthorized\"}";
        } else if (data.matches()) {
            if (dataStatus != null) {
                status = dataStatus;
                body = dataBody;
            } else if (payments.containsKey(data.group(1))) {
                status = 200;
                body = payments.get(data.group(1));
            } else {
                status = 404;
                body = "{\"message\":\"payment request not found\"}";
            }
        } else if (paymentRequestStatus != null) {
            status = paymentRequestStatus;
            body = paymentRequestBody;
        } else {
            String id = "pr-" + requestSequence.incrementAndGet();
            status = 201;
            body = "{\"id\":\"" + id + "\",\"qr\":\"00020101021243650016com.modo\",\"deeplink\":"
                + "\"https://www.modo.com.ar/pagar?id=" + id + "\",\"expiration_date\":\"2026-10-12T12:10:00\"}";
        }
        byte[] answer = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, answer.length);
        exchange.getResponseBody().write(answer);
        exchange.close();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * MODO holds the payment of this payment request, in MODO's final {@code status} (ACCEPTED, REJECTED, REFUNDED or
     * PARTIAL_REFUND), for {@code amount} pesos as MODO writes them ({@code "21000.500"}), with the payer's data.
     */
    public void holdsPayment(String id, String status, String amount) {
        payments.put(id, """
            {"id":"%1$s","qr":"example.qr","status":"%2$s","deeplink":"https://www.preprod.modo.com.ar/pagar/",
             "created_at":"2026-10-15T15:00:19.392Z","expiration_at":600000,
             "expiration_date":"2026-10-15T15:10:19.393Z","sub_payments":null,
             "transaction_data":{"id":"2440561f-d290-421a-a52b-d6e7e1e31da5","payment_request_id":"%1$s",
              "first_name":"Ana","last_name":"Perez","dni":"12345678","payment_method":"CARD",
              "payment_method_detail":{"bin":"516585","type":"CREDIT","bank_name":"BBVA","issuer_name":"master",
               "last_digits":"0008"},
              "paid_at":"2026-10-15T15:00:39.007Z","status":"%3$s","amount":"%4$s"},
             "transactions":[]}
            """.formatted(id, status, "REJECTED".equals(status) ? "REJECTED" : "APPROVED", amount));
    }

    /** Answers every next payment lookup with this status and body. */
    public void answerData(int status, String body) {
        dataStatus = status;
        dataBody = body;
    }

    /**
     * A notification of the payment request as MODO sends it: its fields, the payer's data, and a flattened JWS of
     * them signed with the published key.
     */
    public String notification(String id, String status) {
        ObjectNode body = JSON.createObjectNode()
            .put("id", id)
            .put("status", status)
            .put("external_intention_id", "1001-1")
            .put("amount", 21000.5)
            .put("payment_method", "CARD");
        body.putObject("payer").put("first_name", "Ana").put("last_name", "Perez").put("dni", "12345678");
        body.putObject("card").put("bin", "490698").put("last_digits", "1228");
        body.set("signature", signature(body.toString()));
        return body.toString();
    }

    /** A flattened JWS of {@code payload}, signed with the published key. */
    public ObjectNode signature(String payload) {
        return signature(payload, new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
            signer(signingKey));
    }

    /** A flattened JWS of {@code payload} with this header and signer, for tests of forged signatures. */
    public static ObjectNode signature(String payload, JWSHeader header, JWSSigner signer) {
        var jws = new JWSObject(header, new Payload(payload));
        try {
            jws.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return JSON.createObjectNode()
            .put("protected", jws.getHeader().toBase64URL().toString())
            .put("payload", jws.getPayload().toBase64URL().toString())
            .put("signature", jws.getSignature().toString());
    }

    /** MODO starts signing with a new key, and publishes only that one. */
    public void rotateKey() {
        signingKey = newKey();
    }

    public static RSAKey newKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JWSSigner signer(RSAKey key) {
        try {
            return new RSASSASigner(key);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private RSAKey newKey() {
        return newKey("modo-key-" + keySequence.incrementAndGet());
    }

    /** Answers every next payment request creation with this status and body. */
    void answerPaymentRequests(int status, String body) {
        paymentRequestStatus = status;
        paymentRequestBody = body;
    }

    /** Answers every next token request with this status and body. */
    void answerTokens(int status, String body) {
        tokenStatus = status;
        tokenBody = body;
    }

    /** MODO no longer accepts the tokens it gave. */
    void revokeTokens() {
        validTokens.clear();
    }

    void delay(Duration newDelay) {
        delay = newDelay;
    }

    /** Back to answering normally, holding no payments and with no requests recorded; tokens and keys stay. */
    public void reset() {
        payments.clear();
        requests.clear();
        paymentRequestStatus = null;
        paymentRequestBody = null;
        tokenStatus = null;
        tokenBody = null;
        dataStatus = null;
        dataBody = null;
        delay = Duration.ZERO;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public List<Request> requestsTo(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    Request lastRequest() {
        return requests.getLast();
    }

    public void stop() {
        if (!stopped) {
            stopped = true;
            server.stop(0);
        }
    }

    @Override
    public void close() {
        stop();
    }
}
