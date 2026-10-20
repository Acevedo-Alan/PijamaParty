package com.pijamaparty.store.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStubConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.Browser;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E6-18 (RNF-27): every route that takes an identifier or a token of something a buyer owns, tried over real HTTP
 * by two customers, a guest and the administrator. Besides the answer, each case runs as the intruder the query the
 * route would run without its owner filter, by the victim's identifier only: Row Level Security must leave it 0 rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MercadoPagoStubConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CrossAccessApiTest {

    private static final String PASSWORD = "ocho1234";
    private static final String ADMIN_PASSWORD = "correct horse battery";
    private static final String CART_COOKIE = "__Host-cart_token";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private UUID pijama;
    private UUID pijamaProduct;
    private Person ana;
    private Person bruno;
    private Browser guest;

    CrossAccessApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                       PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                       PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Ana and Bruno, verified customers, and a guest; 10 pijamas in stock; shipping to Santa Fe ARS 4,500. */
    @BeforeEach
    void seed() {
        sellable();
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values ('SANTA_FE', 450000, "
            + "now())");
        ana = signedIn(customer("ana@example.com", "Ana Pérez"));
        bruno = signedIn(customer("bruno@example.com", "Bruno Díaz"));
        guest = new Browser(http);
        guest.get("/api/health");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, withdrawal_request, "
            + "order_tracking_token, order_item, orders, review, cart_item, cart, stock_movement, stock_level, "
            + "product_image, variant, product, size_guide, category, shipping_zone_rate, email_event, email_outbox, "
            + "address, user_token, admin_recovery_code, external_identity, pending_link, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenBrunosOrder_whenAnaOpensItByItsId_thenItIsNotFoundAndTheDatabaseHidesIt() {
        UUID brunos = idOf(buy(bruno.browser(), "bruno@example.com").number());
        UUID guests = idOf(buy(guest, "invitada@example.com").number());

        ResponseEntity<String> opened = ana.browser().get("/api/account/orders/" + brunos);

        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(opened.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(read(opened).get("problem").asText()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(opened.getBody()).doesNotContain("bruno", "Bruno", "San Martín", "30111222");
        assertThat(read(ana.browser().get("/api/account/orders")).get("orders")).isEmpty();
        assertThat(ana.browser().get("/api/account/orders/" + guests).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        for (Actor intruder : new Actor[] {ana.actor(), guestActor()}) {
            hidden(intruder, Row.ORDER, brunos);
            hidden(intruder, Row.ITEMS_OF_ORDER, brunos);
            assertThat(rls.<Integer>as(intruder, sql -> sql.update(
                "update orders set customer_email = 'intruso@example.com' where id = ?", brunos))).isZero();
        }
        hidden(ana.actor(), Row.ORDER, guests);
    }

    @Test
    void givenBrunosAddress_whenAnaEditsOrDeletesItByItsId_thenItIsNotFoundAndStaysAsItWas() {
        String brunosId = read(bruno.browser().post("/api/account/addresses", address("San Martín")))
            .get("id").asText();

        ResponseEntity<String> edited = ana.browser().put("/api/account/addresses/" + brunosId, address("Rivadavia"));
        ResponseEntity<String> deleted = ana.browser().delete("/api/account/addresses/" + brunosId);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read(edited).get("problem").asText()).isEqualTo("ADDRESS_NOT_FOUND");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleted.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(read(ana.browser().get("/api/account/addresses"))).isEmpty();
        assertThat(read(bruno.browser().get("/api/account/addresses")).get(0).get("street").asText())
            .isEqualTo("San Martín");
        UUID id = UUID.fromString(brunosId);
        hidden(ana.actor(), Row.ADDRESS, id);
        assertThat(rls.<Integer>as(ana.actor(),
            sql -> sql.update("update address set street = 'Rivadavia' where id = ?", id)))
            .isZero();
        assertThat(rls.<Integer>as(ana.actor(), sql -> sql.update("delete from address where id = ?", id))).isZero();
        assertThat(owner.queryForObject("select street from address where id = ?", String.class, id))
            .isEqualTo("San Martín");
    }

    @Test
    void givenAnItemInSomeoneElsesCart_whenChangedOrRemovedByItsId_thenItIsNotFoundAndTheCartStaysAsItWas() {
        String brunosItem = itemOf(bruno.browser());
        String anasItem = itemOf(ana.browser());
        String guestsItem = itemOf(guest);

        assertNotFound(ana.browser().patch("/api/cart/items/" + brunosItem, "{\"quantity\":3}"));
        assertNotFound(ana.browser().delete("/api/cart/items/" + brunosItem));
        assertNotFound(guest.patch("/api/cart/items/" + anasItem, "{\"quantity\":3}"));
        assertNotFound(guest.delete("/api/cart/items/" + anasItem));

        assertThat(read(bruno.browser().get("/api/cart")).at("/items/0/quantity").asInt()).isOne();
        assertThat(read(ana.browser().get("/api/cart")).at("/items/0/quantity").asInt()).isOne();
        assertThat(read(guest.get("/api/cart")).get("items")).singleElement()
            .satisfies(item -> assertThat(item.get("id").asText()).isEqualTo(guestsItem));
        UUID brunos = UUID.fromString(brunosItem);
        UUID anas = UUID.fromString(anasItem);
        hidden(ana.actor(), Row.CART_ITEM, brunos);
        hidden(ana.actor(), Row.CART_OF_USER, bruno.id().value());
        hidden(guestActor(), Row.CART_ITEM, anas);
        hidden(guestActor(), Row.CART_OF_USER, ana.id().value());
        assertThat(rls.<Integer>as(ana.actor(),
            sql -> sql.update("update cart_item set quantity = 3 where id = ?", brunos))).isZero();
        assertThat(rls.<Integer>as(guestActor(), sql -> sql.update("delete from cart_item where id = ?", anas)))
            .isZero();
    }

    @Test
    void givenBrunosReviewAwaitingModeration_whenAnaAsksForHerReviewOfThatProduct_thenSheGetsNone() {
        UUID review = UUID.randomUUID();
        owner.update("""
            insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at)
            values (?, ?, ?, 2, 'No me gustó', 'Bruno', 'PENDING', now(), now())
            """, review, pijamaProduct, bruno.id().value());

        ResponseEntity<String> mine = ana.browser().get("/api/account/reviews/" + pijamaProduct);

        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read(mine).get("review").isNull()).isTrue();
        assertThat(mine.getBody()).doesNotContain("No me gustó");
        hidden(ana.actor(), Row.REVIEW, review);
        hidden(guestActor(), Row.REVIEW, review);
    }

    @Test
    void givenTheGuestsTrackingLink_whenUsed_thenItOpensOnlyItsOrderAndAnyOtherTokenIsNotFound() {
        Placed guests = buy(guest, "invitada@example.com");
        Placed brunos = buy(bruno.browser(), "bruno@example.com");
        String wrongToken = "A".repeat(guests.trackingToken().length());

        ResponseEntity<String> own = guest.get("/api/orders/" + guests.trackingToken());

        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read(own).get("number").asLong()).isEqualTo(guests.number());
        assertTrackingNotFound(guest.get("/api/orders/" + wrongToken));
        assertTrackingNotFound(guest.post("/api/orders/" + wrongToken + "/retry-payment", ""));
        assertTrackingNotFound(guest.patch("/api/orders/" + wrongToken + "/email", "{\"email\":\"otro@example.com\"}"));
        assertTrackingNotFound(ana.browser().get("/api/orders/" + wrongToken));
        assertThat(owner.queryForList("select customer_email from orders", String.class))
            .containsExactlyInAnyOrder("invitada@example.com", "bruno@example.com");
        UUID brunosId = idOf(brunos.number());
        hidden(guestActor(guests.trackingToken()), Row.ORDER, brunosId);
        hidden(ana.actor(), Row.ORDER, idOf(guests.number()));
    }

    @Test
    void givenOrdersOfEveryone_whenTheirPaymentsAndCheckoutKeysAreQueried_thenEachSeesNoneOfTheOthers() {
        UUID anas = idOf(buy(ana.browser(), "ana@example.com").number());
        UUID brunos = idOf(buy(bruno.browser(), "bruno@example.com").number());
        UUID guests = idOf(buy(guest, "invitada@example.com").number());
        Actor admin = signedInAdministrator().actor();

        hidden(ana.actor(), Row.CHECKOUT_KEYS_OF_ORDER, brunos);
        hidden(ana.actor(), Row.CHECKOUT_KEYS_OF_ORDER, guests);
        hidden(guestActor(), Row.CHECKOUT_KEYS_OF_ORDER, anas);
        hidden(admin, Row.CHECKOUT_KEYS_OF_ORDER, anas);
        hidden(ana.actor(), Row.PAYMENTS_OF_ORDER, brunos);
        hidden(guestActor(), Row.PAYMENTS_OF_ORDER, anas);
    }

    @Test
    void givenEachSide_whenItCallsTheRoutesOfTheOther_thenItIsDeniedButTheAdministratorReadsEveryOrder() {
        UUID brunos = idOf(buy(bruno.browser(), "bruno@example.com").number());
        itemOf(bruno.browser());
        Person admin = signedInAdministrator();

        ResponseEntity<String> asCustomer = ana.browser().get("/api/admin/orders/" + brunos);
        ResponseEntity<String> asGuest = guest.get("/api/admin/orders/" + brunos);
        ResponseEntity<String> asAdministrator = admin.browser().get("/api/account/orders/" + brunos);
        ResponseEntity<String> inTheBackOffice = admin.browser().get("/api/admin/orders/" + brunos);

        assertThat(asCustomer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asCustomer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(asGuest.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(asAdministrator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asCustomer.getBody()).doesNotContain("bruno", "Bruno");
        assertThat(asGuest.getBody()).doesNotContain("bruno", "Bruno");
        assertThat(inTheBackOffice.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rls.<Long>as(admin.actor(), sql -> sql.queryForObject("select count(*) from orders where id = ?",
            Long.class, brunos))).isOne();
        hidden(admin.actor(), Row.CART_OF_USER, bruno.id().value());
        hidden(admin.actor(), Row.CART_ITEMS_OF_VARIANT, pijama);
    }

    /** The rows exist, seen by the owner of the tables, and the intruder's query by the same identifier sees none. */
    private void hidden(Actor intruder, Row row, UUID value) {
        assertThat(row.count(owner, value)).as("%s exists", row).isPositive();
        assertThat(rls.<Long>as(intruder, sql -> row.count(sql, value))).as("%s seen by the intruder", row).isZero();
    }

    /** A victim's rows by one identifier, as a route would query them without its owner filter. */
    private enum Row {
        ORDER, ITEMS_OF_ORDER, ADDRESS, CART_ITEM, CART_OF_USER, CART_ITEMS_OF_VARIANT, REVIEW,
        CHECKOUT_KEYS_OF_ORDER, PAYMENTS_OF_ORDER;

        long count(JdbcTemplate sql, UUID value) {
            return switch (this) {
                case ORDER -> sql.queryForObject("select count(*) from orders where id = ?", Long.class, value);
                case ITEMS_OF_ORDER ->
                    sql.queryForObject("select count(*) from order_item where order_id = ?", Long.class, value);
                case ADDRESS -> sql.queryForObject("select count(*) from address where id = ?", Long.class, value);
                case CART_ITEM -> sql.queryForObject("select count(*) from cart_item where id = ?", Long.class, value);
                case CART_OF_USER ->
                    sql.queryForObject("select count(*) from cart where user_id = ?", Long.class, value);
                case CART_ITEMS_OF_VARIANT ->
                    sql.queryForObject("select count(*) from cart_item where variant_id = ?", Long.class, value);
                case REVIEW -> sql.queryForObject("select count(*) from review where id = ?", Long.class, value);
                case CHECKOUT_KEYS_OF_ORDER ->
                    sql.queryForObject("select count(*) from idempotency_key where resource_id = ?", Long.class, value);
                case PAYMENTS_OF_ORDER ->
                    sql.queryForObject("select count(*) from payment where order_id = ?", Long.class, value);
            };
        }
    }

    private static void assertNotFound(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private void assertTrackingNotFound(ResponseEntity<String> response) {
        assertNotFound(response);
        assertThat(read(response).get("problem").asText()).isEqualTo("ORDER_NOT_FOUND");
    }

    private record Person(UserId id, Actor actor, Browser browser) {}

    private record Placed(long number, String trackingToken) {}

    private UserId customer(String email, String name) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, ?, 'CUSTOMER', 0, now(), now())
            """, id, email, passwords.hash(PASSWORD).value(), name);
        return UserId.of(id);
    }

    private Person signedIn(UserId id) {
        String email = owner.queryForObject("select email from app_user where id = ?", String.class, id.value());
        var browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post("/api/account/login",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.OK);
        return new Person(id, Actor.customer(id, Optional.empty(), Optional.empty()), browser);
    }

    /** The administrator signs in with its password and enrolls its authenticator app, as the back office does. */
    private Person signedInAdministrator() {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'owner@example.com', true, ?, 'Dueña', 'ADMIN', 0, now(), now())
            """, id, passwords.hash(ADMIN_PASSWORD).value());
        var browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post("/api/admin/auth/login",
            "{\"email\":\"owner@example.com\",\"password\":\"" + ADMIN_PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.OK);
        String secret = read(browser.get("/api/admin/auth/enrollment")).get("secret").asText();
        assertThat(browser.post("/api/admin/auth/enrollment", "{\"code\":\"" + appCode(secret) + "\"}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Person(UserId.of(id), Actor.admin(UserId.of(id)), browser);
    }

    private static String appCode(String secret) {
        try {
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
                .generate(secret, Instant.now().getEpochSecond() / 30);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The guest as the database sees it: its cart token once it has a cart. */
    private Actor guestActor() {
        return Actor.anonymous(guestCart(), Optional.empty());
    }

    /** The guest holding the tracking token of its order, as on its tracking link. */
    private Actor guestActor(String trackingToken) {
        return Actor.anonymous(guestCart(), Optional.of(new TrackingToken(trackingToken)));
    }

    private Optional<CartToken> guestCart() {
        return Optional.ofNullable(guest.cookie(CART_COOKIE)).map(CartToken::new);
    }

    /** One pijama in the cart; answers the item's id. */
    private String itemOf(Browser browser) {
        ResponseEntity<String> added = browser.post("/api/cart/items",
            "{\"variantId\":\"" + pijama + "\",\"quantity\":1}");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(added).at("/items/0/id").asText();
    }

    /** One pijama through the checkout. */
    private Placed buy(Browser browser, String email) {
        itemOf(browser);
        try {
            ResponseEntity<String> placed = browser.post("/api/checkout", json.writeValueAsString(order(email)),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
            assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode body = read(placed);
            return new Placed(body.get("number").asLong(), body.get("trackingToken").asText());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private UUID idOf(long number) {
        return owner.queryForObject("select id from orders where number = ?", UUID.class, number);
    }

    private ObjectNode order(String email) {
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("firstName", "Ana")
            .put("lastName", "Pérez")
            .put("email", email)
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

    private String address(String street) {
        return "{\"street\":\"" + street + "\",\"number\":\"1234\",\"city\":\"Santa Fe\",\"province\":\"SANTA_FE\","
            + "\"postalCode\":\"S3000ABC\",\"primary\":false}";
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void sellable() {
        UUID category = UUID.randomUUID();
        pijamaProduct = UUID.randomUUID();
        pijama = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama', ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, pijamaProduct, category, "pijama-" + pijamaProduct);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 2000000, true, now(), now())
            """, pijama, pijamaProduct, "PP-" + pijama.toString().substring(0, 8));
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 10, now())", pijama);
    }
}
