package com.pijamaparty.store.notifications.adapter.out.resend;

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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Resend's API on a free local port (JDK HTTP server, no network). By default it takes every {@code POST /emails}
 * with a new id, as Resend does. Tests can make it fail or slow down, then {@link #reset()} it.
 */
public final class ResendStub implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final List<Request> delivered = new CopyOnWriteArrayList<>();
    private volatile Integer status;
    private volatile String body;
    private volatile Duration delay = Duration.ZERO;
    private volatile boolean stopped;

    /** What the stub received. */
    public record Request(String method, String path, String authorization, String body) {

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public String to() {
            return json().get("to").get(0).asText();
        }
    }

    public ResendStub() {
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
        var request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"), received);
        requests.add(request);
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int answerStatus = status != null ? status : 200;
        String answerBody = status != null ? body : "{\"id\":\"" + UUID.randomUUID() + "\"}";
        if (answerStatus / 100 == 2) {
            delivered.add(request);
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

    /** Answers every next call with this status and body instead of taking the email. */
    public void answer(int newStatus, String newBody) {
        status = newStatus;
        body = newBody;
    }

    public void delay(Duration newDelay) {
        delay = newDelay;
    }

    /** Back to taking every email at once, with no requests recorded. */
    public void reset() {
        status = null;
        body = null;
        delay = Duration.ZERO;
        requests.clear();
        delivered.clear();
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** The emails it took (answered 2xx), in order. */
    public List<Request> delivered() {
        return List.copyOf(delivered);
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
