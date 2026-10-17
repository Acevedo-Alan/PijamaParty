package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.adapter.in.web.ProvidersStub.Account;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStub;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.notifications.application.SendNextEmail;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.Browser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * UC-27 (T-E4-14) over real HTTP, as a browser does it: the start redirects to the provider with {@code state},
 * {@code nonce} and a PKCE challenge, and the provider's return comes back with a code. The providers are a local stub
 * ({@link ProvidersStub}) that checks the PKCE verifier and signs Google's ID token; Spring Security, Spring Session,
 * Row Level Security and PostgreSQL are real. The browser does not follow redirects: it reads where it is sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class, ProvidersStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ExternalSignInApiTest {

    /** {@code store.public-url} in tests. */
    private static final String STOREFRONT = "http://localhost:5173";
    private static final String ME = "/api/account/me";
    private static final String PASSWORD = "ocho1234";
    private static final Pattern CONFIRMATION_LINK = Pattern.compile("/cuenta/vincular#([A-Za-z0-9_-]{43})");
    private static final Account ANA_AT_GOOGLE = new Account("109876543210987654321", "ana@example.com", true,
        "Ana Pérez");
    private static final Account ANA_AT_FACEBOOK = new Account("10229876543210", "ana@example.com", false,
        "Ana Pérez");

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ProvidersStub providers;
    private final ResendStub resend;
    private final SendNextEmail sendNextEmail;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;

    ExternalSignInApiTest(TestRestTemplate http, ObjectMapper json, ProvidersStub providers, ResendStub resend,
                          SendNextEmail sendNextEmail, PasswordHasher passwords, PostgreSQLContainer<?> postgres) {
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
        this.resend = resend;
        this.sendNextEmail = sendNextEmail;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        providers.reset();
        resend.reset();
        owner.execute("truncate cart_item, cart, stock_movement, stock_level, product_image, variant, product, "
            + "category, email_outbox, address, user_token, external_identity, pending_link, admin_recovery_code, "
            + "app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenTheEnabledProviders_whenTheStorefrontAsks_thenBothAreListed() throws IOException {
        ResponseEntity<String> answer = new Browser(http).get("/api/account/oauth2/providers");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(answer.getBody()).get("providers").toString())
            .isEqualTo("[\"GOOGLE\",\"FACEBOOK\"]");
    }

    @Test
    void givenAVisitor_whenItStartsWithGoogle_thenItIsSentToGoogleWithStateNonceAndAPkceChallenge() {
        Browser browser = new Browser(http);

        Authorization google = start(browser, "google");

        assertThat(google.location()).startsWith("https://accounts.google.com/");
        assertThat(google.parameters()).containsEntry("response_type", "code")
            .containsEntry("client_id", ProvidersStub.GOOGLE_CLIENT_ID)
            .containsEntry("scope", "openid profile email")
            .containsEntry("code_challenge_method", "S256")
            .containsEntry("redirect_uri", "http://localhost:5173/api/account/oauth2/code/google")
            .containsKeys("state", "nonce", "code_challenge");
    }

    @Test
    void givenAVisitorWithoutAnAccount_whenItContinuesWithGoogle_thenAVerifiedAccountIsCreatedAndItIsSignedIn()
        throws IOException {
        Browser browser = new Browser(http);
        String sessionBefore = sessionOf(browser);

        ResponseEntity<String> back = signInWithGoogle(browser, ANA_AT_GOOGLE);

        assertThat(page(back)).isEqualTo("/");
        assertThat(sessionOf(browser)).as("a new session id").isNotEqualTo(sessionBefore);
        JsonNode me = json.readTree(browser.get(ME).getBody());
        assertThat(me.get("email").asText()).isEqualTo("ana@example.com");
        assertThat(me.get("name").asText()).isEqualTo("Ana Pérez");
        assertThat(owner.queryForMap("select email_verified, password_hash from app_user"))
            .containsEntry("email_verified", true).containsEntry("password_hash", null);
        assertThat(owner.queryForMap("select provider, subject, email from external_identity"))
            .containsEntry("provider", "GOOGLE").containsEntry("subject", ANA_AT_GOOGLE.subject())
            .containsEntry("email", "ana@example.com");
        assertThat(owner.queryForObject("select count(*) from spring_session_attributes "
            + "where attribute_name like '%OAuth2%'", Integer.class)).as("nothing of the provider is kept").isZero();
    }

    @Test
    void givenACustomerWithAPassword_whenItContinuesWithGoogleWithTheSameVerifiedEmail_thenItIsLinkedAndSignedIn()
        throws IOException {
        UUID ana = customerWithPassword("ana@example.com", true);
        Browser browser = new Browser(http);

        assertThat(page(signInWithGoogle(browser, ANA_AT_GOOGLE))).isEqualTo("/");

        assertThat(json.readTree(browser.get(ME).getBody()).get("id").asText()).isEqualTo(ana.toString());
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).as("no other account")
            .isOne();
        assertThat(owner.queryForObject("select user_id from external_identity", UUID.class)).isEqualTo(ana);
        assertThat(owner.queryForObject("select password_hash from app_user", String.class))
            .as("a verified account keeps its password").isNotNull();
    }

    @Test
    void givenACustomer_whenItContinuesWithFacebookWithTheSameEmail_thenItSignsInOnlyAfterConfirmingTheEmailedLink() {
        UUID ana = customerWithPassword("ana@example.com", true);
        Browser browser = new Browser(http);

        ResponseEntity<String> back = signInWithFacebook(browser, ANA_AT_FACEBOOK);

        assertThat(page(back)).isEqualTo("/cuenta/ingresar?social=confirm-link");
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(owner.queryForObject("select count(*) from external_identity", Integer.class)).isZero();

        String token = link(sendDueEmails().getFirst());
        assertThat(browser.post("/api/account/link/confirm", "{\"token\":\"" + token + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get(ME).getStatusCode()).as("confirming does not sign in")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(owner.queryForObject("select user_id from external_identity", UUID.class)).isEqualTo(ana);

        assertThat(page(signInWithFacebook(browser, ANA_AT_FACEBOOK))).isEqualTo("/");
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenAVisitorWithoutAnAccount_whenItContinuesWithFacebook_thenAnUnverifiedAccountSignsInAndGetsAVerification() {
        Browser browser = new Browser(http);

        assertThat(page(signInWithFacebook(browser, ANA_AT_FACEBOOK))).isEqualTo("/");

        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.queryForMap("select email_verified, password_hash from app_user"))
            .containsEntry("email_verified", false).containsEntry("password_hash", null);
        assertThat(owner.queryForList("select type from email_outbox", String.class))
            .containsExactly("EMAIL_VERIFICATION");
    }

    @Test
    void givenAFacebookAccountWithoutAnEmail_whenTheVisitorGivesOne_thenTheAccountIsLinkedOnlyOnceItIsConfirmed() {
        Browser browser = new Browser(http);
        var withoutEmail = new Account("10229876543210", null, false, "Ana Pérez");

        assertThat(page(signInWithFacebook(browser, withoutEmail))).isEqualTo("/cuenta/completar-email");
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isZero();

        assertThat(browser.post("/api/account/oauth2/email", "{\"email\":\"ana@example.com\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.post("/api/account/oauth2/email", "{\"email\":\"ana@example.com\"}").getStatusCode())
            .as("the session forgot the external account").isEqualTo(HttpStatus.GONE);
        assertThat(owner.queryForMap("select name, email_verified, password_hash from app_user"))
            .containsEntry("name", "Ana Pérez").containsEntry("email_verified", false)
            .containsEntry("password_hash", null);

        String token = link(sendDueEmails().getFirst());
        browser.post("/api/account/link/confirm", "{\"token\":\"" + token + "\"}");
        assertThat(owner.queryForObject("select email_verified from app_user", Boolean.class)).isTrue();

        assertThat(page(signInWithFacebook(browser, withoutEmail))).isEqualTo("/");
        assertThat(json(browser.get(ME)).get("email").asText()).isEqualTo("ana@example.com");
    }

    @Test
    void givenNoFacebookEmail_whenTheVisitorTypesAnExistingAccountsEmail_thenNothingIsLinkedAndItsOwnerIsNoticed() {
        UUID ana = customerWithPassword("ana@example.com", true);
        Browser browser = new Browser(http);
        var withoutEmail = new Account("10229876543210", null, false, "Someone");
        assertThat(page(signInWithFacebook(browser, withoutEmail))).isEqualTo("/cuenta/completar-email");

        assertThat(browser.post("/api/account/oauth2/email", "{\"email\":\"ana@example.com\"}").getStatusCode())
            .as("the same answer as for an email without an account").isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(owner.queryForObject("select count(*) from pending_link", Integer.class)).isZero();
        assertThat(owner.queryForList("select type from email_outbox where reference = ?", String.class, ana))
            .containsExactly("LINK_ATTEMPT_NOTICE");
        ResendStub.Request notice = sendDueEmails().getFirst();
        assertThat(notice.json().get("to").toString()).contains("ana@example.com");
        assertThat(notice.json().get("text").asText()).contains("/cuenta/ingresar").doesNotContain("/cuenta/vincular");
        assertThat(owner.queryForObject("select count(*) from user_token", Integer.class)).isZero();
        assertThat(owner.queryForObject("select count(*) from external_identity", Integer.class)).isZero();
        assertThat(page(signInWithFacebook(new Browser(http), withoutEmail)))
            .as("the external account is still not linked").isEqualTo("/cuenta/completar-email");
    }

    @Test
    void givenNoExternalAccountWaiting_whenAnEmailIsGiven_thenItIsRefusedAsExpired() {
        Browser browser = new Browser(http);
        browser.get("/api/health");

        ResponseEntity<String> answer = browser.post("/api/account/oauth2/email", "{\"email\":\"ana@example.com\"}");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(json(answer).get("problem").asText()).isEqualTo("EXTERNAL_SIGN_IN_EXPIRED");
    }

    @Test
    void givenAProviderResponseWithAStateThatDoesNotMatch_whenItArrives_thenItIsRefusedWithoutCreatingOrLinking() {
        Browser browser = new Browser(http);
        Authorization google = start(browser, "google");
        providers.nextGoogle(ANA_AT_GOOGLE, google.parameters().get("nonce"), google.parameters().get("code_challenge"));

        ResponseEntity<String> back = browser.get("/api/account/oauth2/code/google?code=abc&state=forged-state");

        assertThat(page(back)).isEqualTo("/cuenta/ingresar?social=failed");
        assertThat(providers.tokenRequests()).as("the code is never exchanged").isZero();
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isZero();
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenTheAdministratorsAccount_whenSomeoneContinuesWithGoogleUsingItsEmail_thenTheBackOfficeIsNotReached() {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'owner@example.com', true, ?, 'Owner', 'ADMIN', 0, now(), now())
            """, UUID.randomUUID(), passwords.hash("correct horse battery").value());
        Browser browser = new Browser(http);

        ResponseEntity<String> back = signInWithGoogle(browser,
            new Account("1", "owner@example.com", true, "Owner"));

        assertThat(page(back)).isEqualTo("/cuenta/ingresar?social=failed");
        assertThat(browser.get("/api/admin/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(owner.queryForObject("select count(*) from external_identity", Integer.class)).isZero();
    }

    @Test
    void givenTheVisitorCancelsAtTheProvider_whenItComesBack_thenItIsToldItCancelled() {
        Browser browser = new Browser(http);
        Authorization google = start(browser, "google");

        ResponseEntity<String> back = browser.get("/api/account/oauth2/code/google?error=access_denied&state="
            + google.parameters().get("state"));

        assertThat(page(back)).isEqualTo("/cuenta/ingresar?social=cancelled");
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isZero();
    }

    @Test
    void givenAGuestCart_whenItsVisitorSignsInWithGoogle_thenTheCartBecomesTheCustomers() {
        UUID camison = sellable();
        Browser browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post("/api/cart/items", "{\"variantId\":\"" + camison + "\",\"quantity\":2}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> back = signInWithGoogle(browser, ANA_AT_GOOGLE);

        assertThat(back.getHeaders().get(HttpHeaders.SET_COOKIE)).as("the browser drops the guest cart token")
            .anySatisfy(cookie -> assertThat(cookie).contains("Max-Age=0").startsWith("__Host-cart_token=;"));
        UUID customer = owner.queryForObject("select id from app_user", UUID.class);
        assertThat(owner.queryForObject("select user_id from cart", UUID.class)).isEqualTo(customer);
        assertThat(json(browser.get("/api/cart")).get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    }

    /** The redirect to the provider and what it carries. */
    private record Authorization(String location, Map<String, String> parameters) {}

    private Authorization start(Browser browser, String provider) {
        ResponseEntity<String> start = browser.get("/api/account/oauth2/authorization/" + provider);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = location(start);
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
        assertThat(facebook.location()).startsWith("https://www.facebook.com/" + ProvidersStub.GRAPH_VERSION + "/");
        providers.nextFacebook(account, facebook.parameters().get("code_challenge"));
        return browser.get("/api/account/oauth2/code/facebook?code=a-code&state="
            + facebook.parameters().get("state"));
    }

    /** The storefront's page the browser is sent back to, always on the storefront's own origin. */
    private static String page(ResponseEntity<String> response) {
        assertThat(location(response)).startsWith(STOREFRONT + "/");
        return location(response).substring(STOREFRONT.length());
    }

    private static String location(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return response.getHeaders().getFirst(HttpHeaders.LOCATION);
    }

    private static String sessionOf(Browser browser) {
        if (browser.cookie("SESSION") == null) {
            browser.get("/api/account/oauth2/authorization/google");
        }
        return browser.cookie("SESSION");
    }

    private UUID customerWithPassword(String email, boolean verified) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, ?, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, id, email, verified, passwords.hash(PASSWORD).value());
        return id;
    }

    /** Sends every email due, as the job does, and answers the ones delivered so far. */
    private List<ResendStub.Request> sendDueEmails() {
        while (!(CurrentActor.callAsSystem(sendNextEmail::execute) instanceof SendNextEmail.Result.NoneDue)) {
            // next one
        }
        return resend.delivered();
    }

    private static String link(ResendStub.Request email) {
        Matcher link = CONFIRMATION_LINK.matcher(email.json().get("text").asText());
        assertThat(link.find()).as("the email carries the link confirmation link").isTrue();
        return link.group(1);
    }

    private JsonNode json(ResponseEntity<String> response) {
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
            values (?, ?, 'Camisón', ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category, "producto-" + product);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 1500000, true, now(), now())
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8));
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 5, now())", variantId);
        return variantId;
    }
}
