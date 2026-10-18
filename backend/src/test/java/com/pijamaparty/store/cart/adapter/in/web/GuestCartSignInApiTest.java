package com.pijamaparty.store.cart.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.platform.testing.Browser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-19 (T-E4-04) over real HTTP: a visitor with a guest cart signs in, and its cart joins the account's. Row Level
 * Security (V32) and PostgreSQL are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuestCartSignInApiTest {

    private static final String CART_COOKIE = CartTokenCookie.NAME;
    private static final String PASSWORD = "ocho1234";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private UUID customer;
    private UUID pijama;
    private UUID camison;
    private UUID bata;

    GuestCartSignInApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                           PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A verified customer, and three variants: 3 pijamas, 5 camisones and 2 batas available. */
    @BeforeEach
    void seed() {
        customer = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'ana@example.com', true, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, customer, passwords.hash(PASSWORD).value());
        pijama = sellable("Pijama", 3);
        camison = sellable("Camisón", 5);
        bata = sellable("Bata", 2);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart, stock_movement, stock_level, product_image, variant, product, "
            + "size_guide, category, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenAGuestCartAndACustomerCart_whenSigningIn_thenTheItemsJoinWithoutExceedingTheStockAndTheGuestCartIsGone() {
        Browser account = signedIn(new Browser(http));
        add(account, pijama, 2);
        add(account, bata, 1);
        account.post("/api/account/logout", null);
        var guest = new Browser(http);
        guest.get("/api/health");
        add(guest, pijama, 2);
        add(guest, camison, 1);
        assertThat(guest.cookie(CART_COOKIE)).isNotNull();

        ResponseEntity<String> login = signIn(guest);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).as("the browser drops the guest cart token")
            .anySatisfy(cookie -> assertThat(cookie).startsWith(CART_COOKIE + "=;").contains("Max-Age=0"));
        assertThat(guest.cookie(CART_COOKIE)).isNull();
        assertThat(lines(guest.get("/api/cart")))
            .as("the pijamas add up to 4, capped at the 3 available; the camisón joins after the account's lines")
            .containsExactly(tuple(pijama.toString(), 3), tuple(bata.toString(), 1), tuple(camison.toString(), 1));
        assertThat(owner.queryForObject("select count(*) from cart where token_hash is not null", Long.class))
            .isZero();

        guest.post("/api/account/logout", null);
        assertThat(lines(guest.get("/api/cart"))).as("signing out starts a new guest cart").isEmpty();
    }

    @Test
    void givenAGuestCartAndNoCustomerCart_whenSigningIn_thenItBecomesTheCustomersCart() {
        var guest = new Browser(http);
        guest.get("/api/health");
        add(guest, camison, 2);

        signIn(guest);

        assertThat(lines(guest.get("/api/cart"))).containsExactly(tuple(camison.toString(), 2));
        assertThat(owner.queryForObject("select user_id from cart", UUID.class)).isEqualTo(customer);
    }

    @Test
    void givenNoGuestCart_whenSigningIn_thenNoCartIsCreatedAndNoCookieIsTouched() {
        ResponseEntity<String> login = signIn(new Browser(http));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()))
            .noneSatisfy(cookie -> assertThat(cookie).startsWith(CART_COOKIE));
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isZero();
    }

    @Test
    void givenAGuestCart_whenTheSignInFails_thenTheCartStaysTheGuests() {
        var guest = new Browser(http);
        guest.get("/api/health");
        add(guest, camison, 1);

        ResponseEntity<String> login = guest.post("/api/account/login",
            "{\"email\":\"ana@example.com\",\"password\":\"wrong-password\"}");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.cookie(CART_COOKIE)).isNotNull();
        assertThat(lines(guest.get("/api/cart"))).containsExactly(tuple(camison.toString(), 1));
    }

    private Browser signedIn(Browser browser) {
        browser.get("/api/health");
        assertThat(signIn(browser).getStatusCode()).isEqualTo(HttpStatus.OK);
        return browser;
    }

    private static ResponseEntity<String> signIn(Browser browser) {
        if (browser.cookie("XSRF-TOKEN") == null) {
            browser.get("/api/health");
        }
        return browser.post("/api/account/login",
            "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}");
    }

    private void add(Browser browser, UUID variantId, int quantity) {
        ResponseEntity<String> added = browser.post("/api/cart/items",
            "{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Variant id and quantity of each line, in the cart's order. */
    private List<Tuple> lines(ResponseEntity<String> cart) {
        assertThat(cart.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode items = read(cart).get("items");
        var lines = new ArrayList<Tuple>();
        items.forEach(item -> lines.add(tuple(item.get("variantId").asText(), item.get("quantity").asInt())));
        return lines;
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private UUID sellable(String name, int available) {
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
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category, name, "producto-" + product);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 1500000, true, now(), now())
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8));
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            available);
        return variantId;
    }
}
