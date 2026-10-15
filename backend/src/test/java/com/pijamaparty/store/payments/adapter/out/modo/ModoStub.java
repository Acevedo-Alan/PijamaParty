package com.pijamaparty.store.payments.adapter.out.modo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The MODO API on a free local port (JDK HTTP server, no network). {@code POST /v2/stores/companies/token} gives a new
 * token per request, valid a week, and {@code POST /v2/payment-requests/} creates a payment request with a new id
 * per call, for a token it gave and has not {@link #revokeTokens() revoked} (otherwise {@code 401}). Tests can make
 * either fail or slow down.
 */
final class ModoStub implements AutoCloseable {

    static final String USERNAME = "test-modo-user";
    static final String PASSWORD = "test-modo-password";
    static final long TOKEN_LIFETIME_SECONDS = 604_800;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
    private final AtomicInteger tokenSequence = new AtomicInteger();
    private final AtomicInteger requestSequence = new AtomicInteger();
    private volatile Integer paymentRequestStatus;
    private volatile String paymentRequestBody;
    private volatile Integer tokenStatus;
    private volatile String tokenBody;
    private volatile Duration delay = Duration.ZERO;
    private volatile boolean stopped;

    /** What the stub received. */
    record Request(String method, String path, String authorization, String userAgent, String body) {

        JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    ModoStub() {
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
        int status;
        String body;
        if (ModoAccessTokens.TOKEN_PATH.equals(path)) {
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

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
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

    List<Request> requests() {
        return List.copyOf(requests);
    }

    List<Request> requestsTo(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    Request lastRequest() {
        return requests.getLast();
    }

    void stop() {
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
