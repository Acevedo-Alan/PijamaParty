package com.pijamaparty.store.payments.adapter.out.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The MercadoPago API on a free local port (JDK HTTP server, no network). By default it creates a preference with a
 * new id per {@code POST /checkout/preferences}, as MercadoPago does, except that a repeated {@code X-Idempotency-Key}
 * gets the preference its first request created (T-E3-17), and shows the payments {@link #holdsPayment}
 * arranged at {@code GET /v1/payments/{id}} (404 for any other). Tests can make it fail or slow down, then
 * {@link #reset()} it.
 */
public final class MercadoPagoStub implements AutoCloseable {

    public static final String INIT_POINT = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=";

    private static final String PAYMENTS_PATH = "/v1/payments/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> payments = new ConcurrentHashMap<>();
    private final Map<String, String> preferencesByKey = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile Integer status;
    private volatile String body;
    private volatile Duration delay = Duration.ZERO;
    private volatile boolean stopped;

    /** What the stub received. */
    public record Request(String method, String path, String authorization, String idempotencyKey, String body) {

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public MercadoPagoStub() {
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
        String idempotencyKey = exchange.getRequestHeaders().getFirst("X-Idempotency-Key");
        requests.add(new Request(exchange.getRequestMethod(), path,
            exchange.getRequestHeaders().getFirst("Authorization"), idempotencyKey, received));
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int answerStatus;
        String answerBody;
        if (status != null) {
            answerStatus = status;
            answerBody = body;
        } else if ("GET".equals(exchange.getRequestMethod()) && path.startsWith(PAYMENTS_PATH)) {
            String payment = payments.get(path.substring(PAYMENTS_PATH.length()));
            answerStatus = payment == null ? 404 : 200;
            answerBody = payment == null ? "{\"message\":\"Payment not found\",\"status\":404}" : payment;
        } else {
            String preference = idempotencyKey == null
                ? "pref-" + sequence.incrementAndGet()
                : preferencesByKey.computeIfAbsent(idempotencyKey, key -> "pref-" + sequence.incrementAndGet());
            answerStatus = 201;
            answerBody = "{\"id\":\"" + preference + "\",\"init_point\":\"" + INIT_POINT + preference + "\","
                + "\"sandbox_init_point\":\"https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id="
                + preference + "\"}";
        }
        byte[] answer = answerBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answerStatus, answer.length);
        exchange.getResponseBody().write(answer);
        exchange.close();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * MercadoPago holds a payment for the order, in one of its statuses ({@code approved}, {@code rejected},
     * {@code pending} ...), with the payer data MercadoPago adds, which the store must ignore.
     */
    public void holdsPayment(String id, long orderNumber, String mercadoPagoStatus, BigDecimal pesos) {
        holdsPaymentJson(id, """
            {"id": %s, "status": "%s", "status_detail": "accredited", "external_reference": "%d",
             "transaction_amount": %s, "currency_id": "ARS", "date_approved": "2026-10-12T12:05:00.000-03:00",
             "payer": {"email": "ana@example.com", "identification": {"type": "DNI", "number": "12345678"}},
             "payment_method_id": "visa", "payment_type_id": "credit_card"}
            """.formatted(id, mercadoPagoStatus, orderNumber, pesos.toPlainString()));
    }

    /** MercadoPago answers this JSON for the payment. */
    public void holdsPaymentJson(String id, String json) {
        payments.put(id, json);
    }

    /** Answers every next call with this status and body instead of a created preference or a payment. */
    public void answer(int newStatus, String newBody) {
        status = newStatus;
        body = newBody;
    }

    public void delay(Duration newDelay) {
        delay = newDelay;
    }

    /** Back to creating preferences at once, with no payments and no requests recorded. */
    public void reset() {
        status = null;
        body = null;
        delay = Duration.ZERO;
        payments.clear();
        preferencesByKey.clear();
        requests.clear();
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public Request lastRequest() {
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
