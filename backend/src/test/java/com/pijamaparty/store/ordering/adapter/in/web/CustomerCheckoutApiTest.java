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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-21 (T-E4-06) over real HTTP: a signed-in customer buys, its order is linked to its account, and the checkout
 * leaves its phone, and its address if asked, for the next one. The session, Row Level Security and PostgreSQL are
 * real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MercadoPagoStubConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CustomerCheckoutApiTest {

    private static final String PASSWORD = "ocho1234";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private UUID customer;
    private UUID pijama;

    CustomerCheckoutApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                            PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A verified customer without a phone or addresses; 5 pijamas in stock; shipping to Santa Fe ARS 4,500. */
    @BeforeEach
    void seed() {
        customer = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'ana@example.com', true, ?, 'Ana Pérez', 'CUSTOMER', 0, now(), now())
            """, customer, passwords.hash(PASSWORD).value());
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
    void givenASignedInCustomer_whenItCompletesAPurchase_thenTheOrderIsLinkedToItsAccountAndItsPhoneIsKept() {
        Browser browser = signedIn();
        assertThat(read(browser.get("/api/account/me")).get("phone").isNull()).isTrue();
        add(browser);

        ResponseEntity<String> placed = checkout(browser, order(false));

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(owner.queryForObject("select user_id from orders where number = ?", UUID.class,
            read(placed).get("number").asLong())).isEqualTo(customer);
        assertThat(read(browser.get("/api/account/me")).get("phone").asText()).isEqualTo("3425551234");
        assertThat(owner.queryForObject("select count(*) from address", Long.class)).isZero();
    }

    @Test
    void givenACustomerThatSavesTheAddress_whenItBuys_thenTheAddressIsItsPrimaryAndTheOrderKeepsItsOwnCopy() {
        Browser browser = signedIn();
        add(browser);

        assertThat(checkout(browser, order(true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode addresses = read(browser.get("/api/account/addresses"));
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).get("street").asText()).isEqualTo("San Martín");
        assertThat(addresses.get(0).get("postalCode").asText()).isEqualTo("S3000ABC");
        assertThat(addresses.get(0).get("primary").asBoolean()).isTrue();
        assertThat(browser.delete("/api/account/addresses/" + addresses.get(0).get("id").asText()).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.queryForObject("select street from orders", String.class)).isEqualTo("San Martín");
    }

    @Test
    void givenAGuestThatAsksToSaveTheAddress_whenItBuys_thenNoAccountIsTouched() {
        var guest = new Browser(http);
        guest.get("/api/health");
        add(guest);

        assertThat(checkout(guest, order(true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(owner.queryForObject("select user_id from orders", UUID.class)).isNull();
        assertThat(owner.queryForObject("select count(*) from address", Long.class)).isZero();
        assertThat(owner.queryForObject("select phone from app_user", String.class)).isNull();
    }

    private Browser signedIn() {
        var browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post("/api/account/login",
            "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.OK);
        return browser;
    }

    private void add(Browser browser) {
        assertThat(browser.post("/api/cart/items", "{\"variantId\":\"" + pijama + "\",\"quantity\":1}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> checkout(Browser browser, ObjectNode body) {
        try {
            return browser.post("/api/checkout", json.writeValueAsString(body),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private ObjectNode order(boolean saveAddress) {
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("name", "Ana Pérez")
            .put("email", "ana@example.com")
            .put("phone", "342 555-1234")
            .put("nationalId", "12.345.678");
        body.putObject("address")
            .put("street", "San Martín")
            .put("number", "1234")
            .putNull("floor")
            .put("city", "Santa Fe")
            .put("province", "SANTA_FE")
            .put("postalCode", "s3000abc");
        body.put("paymentProvider", "MERCADOPAGO");
        body.put("acceptedTerms", true);
        body.put("saveAddress", saveAddress);
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
