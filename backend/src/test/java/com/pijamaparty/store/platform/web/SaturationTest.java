package com.pijamaparty.store.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** RNF-21 and T-E0-11: bounded Tomcat, and 503 with Retry-After instead of hanging when the pool is exhausted. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, SaturationTest.DatabaseRoutes.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SaturationTest {

    private final TestRestTemplate http;
    private final HikariDataSource pool;
    private final ServletWebServerApplicationContext context;
    private final List<Connection> held = new ArrayList<>();

    SaturationTest(TestRestTemplate http, DataSource dataSource, ServletWebServerApplicationContext context)
        throws Exception {
        this.http = http;
        this.pool = dataSource.unwrap(HikariDataSource.class);
        this.context = context;
    }

    @AfterEach
    void releaseConnections() throws Exception {
        for (Connection connection : held) {
            connection.close();
        }
        held.clear();
    }

    @Test
    void givenTheConnectionPoolExhausted_whenAJdbcRequestArrives_thenTheApiAnswers503WithRetryAfterAndDoesNotHang()
        throws Exception {
        exhaustThePool();

        assertAnswers503Quickly("/api/test/public/db/jdbc");
    }

    @Test
    void givenTheConnectionPoolExhausted_whenATransactionalRequestArrives_thenTheApiAnswers503WithRetryAfter()
        throws Exception {
        exhaustThePool();

        assertAnswers503Quickly("/api/test/public/db/jpa");
    }

    @Test
    void givenThePoolExhaustedInsideAFilter_whenARequestArrives_thenTheApiAnswers503WithRetryAfterInProblemJson()
        throws Exception {
        exhaustThePool();

        assertAnswers503Quickly("/api/test/public/db/filter");
    }

    @Test
    void givenThePoolWasExhausted_whenConnectionsAreReleased_thenTheApiRecovers() throws Exception {
        exhaustThePool();
        assertThat(http.getForEntity("/api/test/public/db/jdbc", String.class).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        releaseConnections();

        assertThat(http.getForEntity("/api/test/public/db/jdbc", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenTheEmbeddedServer_whenItRuns_thenTomcatIsBoundedAsRnf21Says() {
        Connector connector = ((TomcatWebServer) context.getWebServer()).getTomcat().getConnector();

        assertThat(connector.getProperty("maxThreads")).isEqualTo(40);
        assertThat(connector.getProperty("minSpareThreads")).isEqualTo(5);
        assertThat(connector.getProperty("maxConnections")).isEqualTo(140);
        assertThat(connector.getProperty("acceptCount")).isEqualTo(100);
        assertThat(connector.getProperty("connectionTimeout")).isEqualTo(10_000);
    }

    private void exhaustThePool() throws Exception {
        for (int i = 0; i < pool.getMaximumPoolSize(); i++) {
            held.add(pool.getConnection());
        }
    }

    private void assertAnswers503Quickly(String path) {
        long start = System.nanoTime();
        ResponseEntity<String> response = http.getForEntity(path, String.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"status\":503");
        // Hikari gives up after its 3 s connection timeout; the request must not hang beyond that.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(8));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(DatabaseRoutes.Controller.class)
    static class DatabaseRoutes {

        /** Touches the database before Spring MVC, so its failure is rendered by the /error controller. */
        @Bean
        FilterRegistrationBean<OncePerRequestFilter> databaseFilter(JdbcTemplate jdbc) {
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws IOException {
                    Integer one = jdbc.queryForObject("select 1", Integer.class);
                    response.getWriter().print(one);
                }
            });
            registration.addUrlPatterns("/api/test/public/db/filter");
            return registration;
        }

        @Bean
        HttpAuthorizationRules publicDatabaseTestRoutes() {
            return registry -> registry.requestMatchers("/api/test/public/db/**").permitAll();
        }

        @RestController
        static class Controller {

            private final JdbcTemplate jdbc;
            private final TransactionTemplate transactions;
            private final EntityManager entityManager;

            Controller(JdbcTemplate jdbc, TransactionTemplate transactions, EntityManager entityManager) {
                this.jdbc = jdbc;
                this.transactions = transactions;
                this.entityManager = entityManager;
            }

            @GetMapping("/api/test/public/db/jdbc")
            Integer jdbc() {
                return jdbc.queryForObject("select 1", Integer.class);
            }

            @GetMapping("/api/test/public/db/jpa")
            Object jpa() {
                return transactions.execute(status -> entityManager.createNativeQuery("select 1").getSingleResult());
            }
        }
    }
}
