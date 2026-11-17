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
import java.util.concurrent.atomic.AtomicLong;

/**
 * MercadoPago's Orders API on a free local port (JDK HTTP server, no network), as the sandbox answered it on 2026-11-17
 * (T-E3-34). {@code POST /v1/orders} charges a card token: it creates an order with a new {@code ORD…} id and the status
 * {@link #cardsAnswer} chose (processed by default), for the request's amount and {@code external_reference}; a refused
 * card ({@code failed}) answers {@code 402} with the order in {@code data}, and a repeated {@code X-Idempotency-Key}
 * gets the order its first request created. {@code GET /v1/orders/{id}} shows those orders and the ones
 * {@link #holdsOrder} arranged, and answers {@code 400} for any other id, as the Orders API does. Tests can make it
 * fail or slow down, then {@link #reset()} it.
 */
public final class MercadoPagoStub implements AutoCloseable {

    private static final String ORDERS = "/v1/orders";
    private static final String ORDERS_PATH = ORDERS + "/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> orders = new ConcurrentHashMap<>();
    private final Map<String, String> ordersByKey = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1_000_000);
    private volatile String cardStatus = "processed";
    private volatile String cardStatusDetail = "accredited";
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
        } else if ("GET".equals(exchange.getRequestMethod()) && path.startsWith(ORDERS_PATH)) {
            String order = orders.get(path.substring(ORDERS_PATH.length()));
            answerStatus = order == null ? 400 : 200;
            answerBody = order == null
                ? "{\"errors\":[{\"code\":\"invalid_path_param\",\"message\":\"order_id is invalid\"}]}"
                : order;
        } else if ("POST".equals(exchange.getRequestMethod()) && ORDERS.equals(path)
            && JSON.readTree(received).path("transactions").path("payments").path(0).path("payment_method")
                .has("issuer_id")) {
            // As the Orders API: an unknown property of the payment method is refused (seen 2026-11-17).
            answerStatus = 400;
            answerBody = "{\"errors\":[{\"code\":\"unsupported_properties\",\"message\":\"Properties not supported\"}]}";
        } else if ("POST".equals(exchange.getRequestMethod()) && ORDERS.equals(path)) {
            String id = idempotencyKey == null
                ? newId()
                : ordersByKey.computeIfAbsent(idempotencyKey, key -> newId());
            JsonNode charge = JSON.readTree(received);
            orders.computeIfAbsent(id, key -> orderJson(key, cardStatus, cardStatusDetail,
                charge.path("external_reference").asText(), new BigDecimal(charge.path("total_amount").asText())));
            String order = orders.get(id);
            boolean refused = order.contains("\"status\": \"failed\"");
            answerStatus = refused ? 402 : 201;
            answerBody = refused
                ? "{\"errors\": [{\"code\": \"failed\", \"message\": \"The following transactions failed\"}], "
                    + "\"data\": " + order + "}"
                : order;
        } else {
            answerStatus = 404;
            answerBody = "{\"errors\":[{\"code\":\"not_found\",\"message\":\"not found\"}]}";
        }
        byte[] answer = answerBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answerStatus, answer.length);
        exchange.getResponseBody().write(answer);
        exchange.close();
    }

    private String newId() {
        return "ORDTST01STUB%018d".formatted(sequence.incrementAndGet());
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * MercadoPago holds an order for the store's order, in one of its statuses ({@code processed}, {@code failed},
     * {@code processing} ...; {@code processed} is accredited), with the payer data MercadoPago adds, which the store
     * must ignore.
     */
    public void holdsOrder(String id, long orderNumber, String orderStatus, BigDecimal pesos) {
        holdsOrderJson(id, orderJson(id, orderStatus, detailOf(orderStatus), Long.toString(orderNumber), pesos));
    }

    private static String detailOf(String orderStatus) {
        return switch (orderStatus) {
            case "processed" -> "accredited";
            case "processing" -> "in_process";
            case "failed" -> "rejected_by_issuer";
            default -> orderStatus;
        };
    }

    /** The order's own detail mirrors its payment's, as the sandbox answers: failed orders say {@code failed}. */
    private static String orderJson(String id, String orderStatus, String paymentDetail, String reference,
                                    BigDecimal pesos) {
        String orderDetail = "failed".equals(orderStatus) ? "failed" : paymentDetail;
        String amount = pesos.setScale(2).toPlainString();
        return """
            {"id": "%s", "type": "online", "processing_mode": "automatic", "external_reference": "%s",
             "total_amount": "%s", "total_paid_amount": "%s", "currency": "ARS", "country_code": "ARG",
             "status": "%s", "status_detail": "%s",
             "payer": {"email": "ana@example.com", "identification": {"type": "DNI", "number": "12345678"}},
             "transactions": {"payments": [{"id": "PAY01STUB%s", "amount": "%s", "status": "%s",
               "status_detail": "%s", "payment_method": {"id": "visa", "type": "credit_card", "installments": 1}}]}}
            """.formatted(id, reference, amount, "processed".equals(orderStatus) ? amount : "0.00", orderStatus,
            orderDetail, id.substring(Math.max(0, id.length() - 12)), amount, orderStatus, paymentDetail);
    }

    /**
     * The next cards charged get this order status and payment detail, as the Orders API answers them:
     * {@code failed} with {@code insufficient_amount} (a {@code 402}), {@code processing} with {@code in_process} ...
     */
    public void cardsAnswer(String orderStatus, String paymentStatusDetail) {
        cardStatus = orderStatus;
        cardStatusDetail = paymentStatusDetail;
    }

    /** MercadoPago answers this JSON for the order. */
    public void holdsOrderJson(String id, String json) {
        orders.put(id, json);
    }

    /** Answers every next call with this status and body instead of an order. */
    public void answer(int newStatus, String newBody) {
        status = newStatus;
        body = newBody;
    }

    public void delay(Duration newDelay) {
        delay = newDelay;
    }

    /** Back to approving cards at once, with no orders and no requests recorded. */
    public void reset() {
        status = null;
        body = null;
        delay = Duration.ZERO;
        cardStatus = "processed";
        cardStatusDetail = "accredited";
        orders.clear();
        ordersByKey.clear();
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
