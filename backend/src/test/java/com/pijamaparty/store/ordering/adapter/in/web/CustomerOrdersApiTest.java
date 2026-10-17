package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStubConfiguration;
import com.pijamaparty.store.platform.testing.Browser;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-22 (T-E4-07) over real HTTP: a signed-in customer opens "Mis pedidos" and one of its orders, placed through the
 * checkout. The session, Row Level Security and PostgreSQL are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MercadoPagoStubConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CustomerOrdersApiTest {

    private static final String ORDERS = "/api/account/orders";
    private static final String PASSWORD = "ocho1234";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private UUID pijama;

    CustomerOrdersApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                          PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Two verified customers, Ana and Bruno; 5 pijamas in stock; shipping to Santa Fe ARS 4,500. */
    @BeforeEach
    void seed() {
        customer("ana@example.com", "Ana Pérez");
        customer("bruno@example.com", "Bruno Díaz");
        pijama = sellable();
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values ('SANTA_FE', 450000, "
            + "now())");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, withdrawal_request, "
            + "order_tracking_token, order_item, orders, cart_item, cart, stock_movement, stock_level, product_image, "
            + "variant, product, category, shipping_zone_rate, email_outbox, address, user_token, admin_recovery_code, "
            + "external_identity, pending_link, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenACustomerWithTwoOrders_whenItOpensMyOrders_thenItSeesBothNewestFirst() {
        Browser ana = signedIn("ana@example.com");
        long first = buy(ana);
        long second = buy(ana);
        buy(signedIn("bruno@example.com"));

        ResponseEntity<String> listed = ana.get(ORDERS);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getHeaders().getCacheControl()).contains("no-store");
        JsonNode body = read(listed);
        assertThat(body.get("orders")).hasSize(2);
        assertThat(body.get("orders").get(0).get("number").asLong()).isEqualTo(second);
        assertThat(body.get("orders").get(1).get("number").asLong()).isEqualTo(first);
        assertThat(body.get("orders").get(0).get("status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(body.get("orders").get(0).get("totalCents").asLong()).isEqualTo(2_450_000);
        assertThat(body.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    void givenAShippedOrder_whenTheCustomerOpensIt_thenItSeesItemsAddressStatusAndTrackingNumber() {
        Browser ana = signedIn("ana@example.com");
        long number = buy(ana);
        owner.update("update orders set status = 'SHIPPED', shipment_tracking_number = 'CA123456789AR' "
            + "where number = ?", number);

        ResponseEntity<String> opened = ana.get(ORDERS + "/" + idOf(number));

        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(opened.getHeaders().getCacheControl()).contains("no-store");
        JsonNode order = read(opened);
        assertThat(order.get("number").asLong()).isEqualTo(number);
        assertThat(order.get("status").asText()).isEqualTo("SHIPPED");
        assertThat(order.get("shipmentTrackingNumber").asText()).isEqualTo("CA123456789AR");
        assertThat(order.get("items")).hasSize(1);
        assertThat(order.get("items").get(0).get("productName").asText()).isEqualTo("Pijama");
        assertThat(order.get("items").get(0).get("lineTotalCents").asLong()).isEqualTo(2_000_000);
        assertThat(order.get("address").get("street").asText()).isEqualTo("San Martín");
        assertThat(order.get("address").get("floor").isNull()).isTrue();
        assertThat(order.get("address").get("postalCode").asText()).isEqualTo("S3000ABC");
        assertThat(order.get("address").get("province").asText()).isEqualTo("SANTA_FE");
        assertThat(order.get("totalCents").asLong()).isEqualTo(2_450_000);
        assertThat(opened.getBody()).as("never the national ID, the email or the phone")
            .doesNotContain("30111222", "ana@example.com", "3425551234");
    }

    @Test
    void givenACustomer_whenItTriesToOpenAnotherPersonsOrder_thenItIsDenied() {
        long brunos = buy(signedIn("bruno@example.com"));
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> opened = ana.get(ORDERS + "/" + idOf(brunos));

        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(opened.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(read(opened).get("problem").asText()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(opened.getBody()).doesNotContain("bruno", "Bruno", "San Martín");
        assertThat(read(ana.get(ORDERS)).get("orders")).isEmpty();
    }

    @Test
    void givenAGuestOrder_whenACustomerTriesToOpenIt_thenItIsNotFound() {
        var guest = new Browser(http);
        guest.get("/api/health");
        long guests = buy(guest);

        // Bruno: the guest bought with Ana's email, so Ana's sign-in would link the order (T-E4-08).
        ResponseEntity<String> opened = signedIn("bruno@example.com").get(ORDERS + "/" + idOf(guests));

        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenAnUnknownOrder_whenTheCustomerOpensIt_thenItIsNotFound() {
        ResponseEntity<String> opened = signedIn("ana@example.com").get(ORDERS + "/" + UUID.randomUUID());

        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read(opened).get("problem").asText()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void givenAVisitorNotSignedIn_whenItAsksForMyOrders_thenItIsUnauthorized() {
        var visitor = new Browser(http);
        visitor.get("/api/health");

        assertThat(visitor.get(ORDERS).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(visitor.get(ORDERS + "/" + UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenAPageOutOfRange_whenListed_thenItIsABadRequest() {
        Browser ana = signedIn("ana@example.com");

        assertThat(ana.get(ORDERS + "?page=-1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ana.get(ORDERS + "?page=1001").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ana.get(ORDERS + "?page=1000").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void customer(String email, String name) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, ?, 'CUSTOMER', 0, now(), now())
            """, UUID.randomUUID(), email, passwords.hash(PASSWORD).value(), name);
    }

    private Browser signedIn(String email) {
        var browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post("/api/account/login",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.OK);
        return browser;
    }

    /** One pijama through the checkout; answers the order's number. */
    private long buy(Browser browser) {
        assertThat(browser.post("/api/cart/items", "{\"variantId\":\"" + pijama + "\",\"quantity\":1}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            ResponseEntity<String> placed = browser.post("/api/checkout", json.writeValueAsString(order()),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
            assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            return read(placed).get("number").asLong();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private UUID idOf(long number) {
        return owner.queryForObject("select id from orders where number = ?", UUID.class, number);
    }

    private ObjectNode order() {
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("name", "Ana Pérez")
            .put("email", "ana@example.com")
            .put("phone", "342 555-1234")
            .put("nationalId", "30.111.222");
        body.putObject("address")
            .put("street", "San Martín")
            .put("number", "1234")
            .putNull("floor")
            .put("city", "Santa Fe")
            .put("province", "SANTA_FE")
            .put("postalCode", "s3000abc");
        body.put("paymentProvider", "MERCADOPAGO");
        body.put("acceptedTerms", true);
        return body;
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private UUID sellable() {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama', ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category, "pijama-" + product);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 2000000, true, now(), now())
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8));
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 5, now())", variantId);
        return variantId;
    }
}
