package com.pijamaparty.store;

import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * T-E6-01: what the end-to-end tests need from the backend besides the {@code e2e} profile's properties. The clock
 * the application reads can be moved forward, and a small HTTP server on the loopback interface, outside the
 * application's own server and its security, lets the tests do so. Test sources only: never part of the jar.
 */
@TestConfiguration(proxyBeanMethods = false)
class E2eConfiguration {

    /** Takes precedence over {@link TimeConfiguration}'s clock wherever a {@link Clock} is injected. */
    @Bean
    @Primary
    AdjustableClock adjustableClock() {
        return new AdjustableClock(Clock.system(TimeConfiguration.STORE_ZONE));
    }

    @Bean(destroyMethod = "stop")
    ClockControl clockControl(AdjustableClock clock, @Value("${store.e2e.clock-port}") int port) throws IOException {
        return ClockControl.start(clock, port);
    }

    /**
     * {@code POST /clock/advance?minutes=N} moves the clock forward, {@code POST /clock/reset} brings it back to the
     * system time and {@code GET /clock} answers the current instant.
     */
    static final class ClockControl {

        private final HttpServer server;

        private ClockControl(HttpServer server) {
            this.server = server;
        }

        static ClockControl start(AdjustableClock clock, int port) throws IOException {
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            server.createContext("/clock", exchange -> {
                try (exchange) {
                    handle(clock, exchange);
                }
            });
            server.start();
            return new ClockControl(server);
        }

        private static void handle(AdjustableClock clock, HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (method.equals("POST") && path.equals("/clock/advance")) {
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.matches("minutes=[0-9]{1,4}")) {
                    answer(exchange, 400, "minutes=N expected");
                    return;
                }
                clock.advance(Duration.ofMinutes(Long.parseLong(query.substring("minutes=".length()))));
            } else if (method.equals("POST") && path.equals("/clock/reset")) {
                clock.reset();
            } else if (!(method.equals("GET") && path.equals("/clock"))) {
                answer(exchange, 404, "unknown");
                return;
            }
            answer(exchange, 200, clock.instant().toString());
        }

        private static void answer(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        void stop() {
            server.stop(0);
        }
    }
}
