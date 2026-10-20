package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.adapter.in.web.ProvidersStub.Account;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.platform.testing.Browser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-28 (T-E4-16) over real HTTP: the customer downloads its data and deletes its account, confirmed with its
 * password or by signing in again with Google (the local stub of {@link ExternalSignInApiTest}). Spring Security,
 * Spring Session, the listeners of the other modules, Row Level Security and PostgreSQL are real. The browser does not
 * follow redirects: it reads where it is sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class, ProvidersStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CustomerDataApiTest {

    /** {@code store.public-url} in tests. */
    private static final String STOREFRONT = "http://localhost:5173";
    private static final String ACCOUNT = "/api/account";
    private static final String DATA = "/api/account/data";
    private static final String LOGIN = "/api/account/login";
    private static final String PASSWORD = "ocho1234";
    private static final Account ANA_AT_GOOGLE = new Account("109876543210987654321", "ana@example.com", true,
        "Ana Pérez");
    private static final Account SOMEONE_AT_GOOGLE = new Account("200000000000000000001", "otra@example.com", true,
        "Otra Persona");
    private static final Account ANA_AT_FACEBOOK = new Account("10229876543210", "ana.perez@example.com", false,
        "Ana Pérez");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ProvidersStub providers;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;

    CustomerDataApiTest(TestRestTemplate http, ObjectMapper json, ProvidersStub providers, PasswordHasher passwords,
                        PostgreSQLContainer<?> postgres) {
        var noRedirects = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        this.http = new TestRestTemplate(new RestTemplateBuilder()
            .rootUri(http.getRootUri())
            .requestFactory(() -> noRedirects));
        this.json = json;
        this.providers = providers;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        providers.reset();
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
        owner.execute("truncate cart_item, cart, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    // --- Acceptance criteria of UC-28 ---

    @Test
    void givenACustomerWithOrders_whenItDeletesItsAccount_thenItCannotSignInItsRowsAreGoneAndItsOrdersStayUnlinked() {
        UUID ana = customer("ana@example.com");
        address(ana);
        cart(ana);
        UUID order = order(ana, "ana@example.com", "30123456");
        Browser browser = signedIn("ana@example.com");

        ResponseEntity<String> deleted = browser.delete(ACCOUNT, "{\"password\":\"" + PASSWORD + "\"}");

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get("/api/account/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signInWithPassword("ana@example.com").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("select count(*) from app_user where id = ?", ana)).isZero();
        assertThat(count("select count(*) from address where user_id = ?", ana)).isZero();
        assertThat(count("select count(*) from cart where user_id = ?", ana)).isZero();
        Map<String, Object> kept = owner.queryForMap(
            "select user_id, unlinked_at, customer_first_name, customer_last_name, national_id from orders where id = ?",
            order);
        assertThat(kept.get("user_id")).as("without a link to the account").isNull();
        assertThat(kept.get("unlinked_at")).isNotNull();
        assertThat(kept).containsEntry("customer_first_name", "Ana").containsEntry("customer_last_name", "Pérez")
            .containsEntry("national_id", "30123456");
    }

    @Test
    void givenTwoCustomers_whenOneDownloadsItsData_thenItGetsItsOwnDataAndNoneOfTheOther() {
        UUID ana = customer("ana@example.com");
        UUID bruno = customer("bruno@example.com");
        address(ana);
        address(bruno);
        order(ana, "ana@example.com", "30123456");
        order(bruno, "bruno@example.com", "27999888");
        Browser browser = signedIn("ana@example.com");

        ResponseEntity<String> download = browser.get(DATA);

        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("mis-datos.json");
        assertThat(download.getHeaders().getCacheControl()).contains("no-store");
        JsonNode data = json(download);
        assertThat(data.hasNonNull("exportedAt")).isTrue();
        assertThat(data.get("profile").get("email").asText()).isEqualTo("ana@example.com");
        assertThat(data.get("profile").get("hasPassword").asBoolean()).isTrue();
        assertThat(data.get("addresses")).hasSize(1);
        assertThat(data.get("orders")).hasSize(1);
        JsonNode order = data.get("orders").get(0);
        assertThat(order.get("buyer").get("firstName").asText()).isEqualTo("Ana");
        assertThat(order.get("buyer").get("lastName").asText()).isEqualTo("Pérez");
        assertThat(order.get("buyer").get("nationalId").asText()).isEqualTo("30123456");
        assertThat(order.get("items")).hasSize(1);
        assertThat(order.get("items").get(0).get("productName").asText()).isEqualTo("Pijama Luna");
        assertThat(order.get("totalCents").asLong()).isEqualTo(2_300_000);
        assertThat(data.get("reviews")).isEmpty();
        assertThat(download.getBody()).doesNotContain("bruno@example.com").doesNotContain("27999888");
    }

    @Test
    void givenADeletedAccount_whenSomeoneRegistersAgainWithItsEmail_thenTheNewAccountDoesNotSeeThePreviousOrders() {
        UUID ana = customer("ana@example.com");
        order(ana, "ana@example.com", "30123456");
        Browser before = signedIn("ana@example.com");
        assertThat(before.delete(ACCOUNT, "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        Browser again = new Browser(http);
        again.get("/api/health");
        assertThat(again.post("/api/account/register",
            "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\",\"name\":\"Ana\"}").getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        owner.update("update app_user set email_verified = true where email = 'ana@example.com'");
        assertThat(again.post(LOGIN, "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(json(again.get("/api/account/orders")).get("orders")).isEmpty();
        assertThat(count("select count(*) from orders where user_id is null and unlinked_at is not null", null))
            .isOne();
    }

    // --- Confirming the deletion ---

    @Test
    void givenAWrongPasswordOrNoConfirmation_whenDeleting_thenItIsForbiddenAndTheAccountStays() {
        UUID ana = customer("ana@example.com");
        Browser browser = signedIn("ana@example.com");

        ResponseEntity<String> wrong = browser.delete(ACCOUNT, "{\"password\":\"no-es-esta\"}");
        ResponseEntity<String> none = browser.delete(ACCOUNT);

        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(wrong).get("problem").asText()).isEqualTo("WRONG_PASSWORD");
        assertThat(none.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(none).get("problem").asText()).isEqualTo("REAUTHENTICATION_REQUIRED");
        assertThat(count("select count(*) from app_user where id = ? and failed_login_attempts = 0", ana)).isOne();
        assertThat(browser.get("/api/account/me").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenACustomerWithoutPassword_whenItConfirmsWithGoogle_thenItDeletesWithoutPasswordInTheSameSession() {
        Browser ana = new Browser(http);
        assertThat(page(signInWithGoogle(ana, ANA_AT_GOOGLE))).isEqualTo("/");
        String session = ana.cookie("SESSION");

        assertThat(ana.post("/api/account/oauth2/reauthenticate/google", null).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(page(signInWithGoogle(ana, ANA_AT_GOOGLE))).isEqualTo("/cuenta?social=reauthenticated");
        assertThat(ana.cookie("SESSION")).as("the same session").isEqualTo(session);

        assertThat(ana.delete(ACCOUNT).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("select count(*) from app_user", null)).isZero();
        assertThat(count("select count(*) from external_identity", null)).isZero();
    }

    @Test
    void givenAConfirmationAsked_whenTheBrowserGoesToTheProvider_thenOnlyThatTripAsksForTheCredentialsAgain() {
        Browser ana = new Browser(http);
        signInWithGoogle(ana, ANA_AT_GOOGLE);
        assertThat(start(ana, "google").parameters()).as("a sign-in").doesNotContainKeys("prompt", "max_age");

        ana.post("/api/account/oauth2/reauthenticate/google", null);
        assertThat(start(ana, "google").parameters()).containsEntry("prompt", "login").containsEntry("max_age", "0");

        Browser beto = new Browser(http);
        signInWithFacebook(beto, ANA_AT_FACEBOOK);
        assertThat(start(beto, "facebook").parameters()).as("a sign-in").doesNotContainKey("auth_type");
        beto.post("/api/account/oauth2/reauthenticate/facebook", null);
        assertThat(start(beto, "facebook").parameters()).containsEntry("auth_type", "reauthenticate");
    }

    @Test
    void givenAnotherGoogleAccount_whenTheCustomerConfirmsWithIt_thenItFailsAndTheDeletionStillNeedsAConfirmation() {
        Browser ana = new Browser(http);
        signInWithGoogle(ana, ANA_AT_GOOGLE);

        ana.post("/api/account/oauth2/reauthenticate/google", null);
        assertThat(page(signInWithGoogle(ana, SOMEONE_AT_GOOGLE)))
            .isEqualTo("/cuenta?social=reauthentication-failed");

        ResponseEntity<String> refused = ana.delete(ACCOUNT);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(refused).get("problem").asText()).isEqualTo("REAUTHENTICATION_REQUIRED");
        assertThat(json(ana.get("/api/account/me")).get("email").asText()).isEqualTo("ana@example.com");
        assertThat(count("select count(*) from app_user", null)).as("nothing was created or linked").isOne();
    }

    @Test
    void givenAConfirmationAskedFromMiCuenta_whenTheCustomerCancelsAtGoogle_thenItGoesBackThere() {
        Browser ana = new Browser(http);
        signInWithGoogle(ana, ANA_AT_GOOGLE);
        ana.post("/api/account/oauth2/reauthenticate/google", null);
        Authorization google = start(ana, "google");

        ResponseEntity<String> back = ana.get("/api/account/oauth2/code/google?error=access_denied&state="
            + google.parameters().get("state"));

        assertThat(page(back)).isEqualTo("/cuenta?social=reauthentication-cancelled");
        assertThat(ana.delete(ACCOUNT).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void givenAProviderTheCustomerHasNotLinked_whenItAsksToConfirmWithIt_thenItIsNotFound() {
        customer("ana@example.com");
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> google = ana.post("/api/account/oauth2/reauthenticate/google", null);
        ResponseEntity<String> apple = ana.post("/api/account/oauth2/reauthenticate/apple", null);

        assertThat(google.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(google).get("problem").asText()).isEqualTo("IDENTITY_NOT_FOUND");
        assertThat(apple.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(apple).get("problem").asText()).isEqualTo("PROVIDER_NOT_ENABLED");
    }

    @Test
    void givenTheCustomerSignedInOnTwoBrowsers_whenItDeletesItsAccountOnOne_thenTheOtherSessionEndsToo() {
        customer("ana@example.com");
        Browser phone = signedIn("ana@example.com");
        Browser laptop = signedIn("ana@example.com");

        assertThat(phone.delete(ACCOUNT, "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(laptop.get("/api/account/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenAVisitorWhoIsNotSignedIn_whenItDownloadsOrDeletes_thenItIsUnauthorized() {
        Browser guest = new Browser(http);
        guest.get("/api/health");

        assertThat(guest.get(DATA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.delete(ACCOUNT, "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.post("/api/account/oauth2/reauthenticate/google", null).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Helpers ---

    private UUID customer(String email) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana Pérez', 'CUSTOMER', 0, now(), now())
            """, id, email, passwords.hash(PASSWORD).value());
        return id;
    }

    private void address(UUID customer) {
        owner.update("""
            insert into address (id, user_id, street, street_number, city, province, postal_code, is_primary,
                                 created_at, updated_at)
            values (?, ?, 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', 'S3000ABC', true, now(), now())
            """, UUID.randomUUID(), customer);
    }

    private void cart(UUID customer) {
        owner.update("insert into cart (id, user_id, created_at, updated_at) values (?, ?, now(), now())",
            UUID.randomUUID(), customer);
    }

    /** An order of the customer with one line, as its checkout leaves it. */
    private UUID order(UUID customer, String email, String nationalId) {
        UUID id = UUID.randomUUID();
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        owner.update("""
            insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name,
                                customer_last_name, customer_email,
                                customer_phone, national_id, street, street_number, city, province, postal_code,
                                subtotal_cents, discount_cents, shipping_cents, total_cents, payment_provider,
                                terms_version, reservation_expires_at, created_at, updated_at)
            values (?, nextval('order_number_seq'), ?, ?, 'AWAITING_PAYMENT', 'Ana', 'Pérez', ?, '3425551234', ?,
                    'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000', 2000000, 0, 300000, 2300000, 'MERCADOPAGO',
                    '2026-10', now() + interval '30 minutes', now(), now())
            """, id, customer, HexFormat.of().formatHex(token), email, nationalId);
        owner.update("""
            insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color,
                                    unit_price_cents, quantity)
            values (?, ?, 0, ?, 'PL-M-ROSA', 'Pijama Luna', 'M', 'Rosa', 2000000, 1)
            """, UUID.randomUUID(), id, UUID.randomUUID());
        return id;
    }

    private int count(String query, UUID customer) {
        Integer count = customer == null
            ? owner.queryForObject(query, Integer.class)
            : owner.queryForObject(query, Integer.class, customer);
        return count == null ? 0 : count;
    }

    /** The verified customer with a password, signed in with it. */
    private Browser signedIn(String email) {
        Browser browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post(LOGIN, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        return browser;
    }

    private ResponseEntity<String> signInWithPassword(String email) {
        Browser browser = new Browser(http);
        browser.get("/api/health");
        return browser.post(LOGIN, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private record Authorization(String location, Map<String, String> parameters) {}

    private Authorization start(Browser browser, String provider) {
        ResponseEntity<String> start = browser.get("/api/account/oauth2/authorization/" + provider);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = start.getHeaders().getFirst(HttpHeaders.LOCATION);
        var parameters = new LinkedHashMap<String, String>();
        UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams().forEach((name, values) ->
            parameters.put(name, URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
        return new Authorization(location, parameters);
    }

    private ResponseEntity<String> signInWithGoogle(Browser browser, Account account) {
        Authorization google = start(browser, "google");
        providers.nextGoogle(account, google.parameters().get("nonce"), google.parameters().get("code_challenge"));
        return browser.get("/api/account/oauth2/code/google?code=a-code&state=" + google.parameters().get("state"));
    }

    private ResponseEntity<String> signInWithFacebook(Browser browser, Account account) {
        Authorization facebook = start(browser, "facebook");
        providers.nextFacebook(account, facebook.parameters().get("code_challenge"));
        return browser.get("/api/account/oauth2/code/facebook?code=a-code&state="
            + facebook.parameters().get("state"));
    }

    /** The storefront's page the browser is sent back to. */
    private static String page(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).startsWith(STOREFRONT + "/");
        return location.substring(STOREFRONT.length());
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
