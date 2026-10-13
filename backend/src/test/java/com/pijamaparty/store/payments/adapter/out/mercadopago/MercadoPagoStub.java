package com.pijamaparty.store.payments.adapter.out.mercadopago;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The MercadoPago API on a free local port (JDK HTTP server, no network). By default it creates a preference with a
 * new id per call, as MercadoPago does; tests can make it fail or slow down, then {@link #reset()} it.
 */
public final class MercadoPagoStub implements AutoCloseable {

    public static final String INIT_POINT = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile Integer status;
    private volatile String body;
    private volatile Duration delay = Duration.ZERO;
    private volatile boolean stopped;

    /** What the stub received. */
    public record Request(String path, String authorization, String body) {

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
        requests.add(new Request(exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"), received));
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String preference = "pref-" + sequence.incrementAndGet();
        int answerStatus = status == null ? 201 : status;
        String answerBody = body == null
            ? "{\"id\":\"" + preference + "\",\"init_point\":\"" + INIT_POINT + preference + "\","
                + "\"sandbox_init_point\":\"https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id="
                + preference + "\"}"
            : body;
        byte[] answer = answerBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answerStatus, answer.length);
        exchange.getResponseBody().write(answer);
        exchange.close();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Answers every next call with this status and body instead of a created preference. */
    public void answer(int newStatus, String newBody) {
        status = newStatus;
        body = newBody;
    }

    public void delay(Duration newDelay) {
        delay = newDelay;
    }

    /** Back to creating preferences at once, with no requests recorded. */
    public void reset() {
        status = null;
        body = null;
        delay = Duration.ZERO;
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
