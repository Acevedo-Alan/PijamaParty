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
import java.util.HashMap;
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
 * UC-27 (T-E4-15) over real HTTP: "Mi cuenta" lists, unlinks and links the customer's external accounts and creates
 * its password. The providers are the local stub of {@link ExternalSignInApiTest}; Spring Security, Spring Session,
 * Row Level Security and PostgreSQL are real. The browser does not follow redirects: it reads where it is sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class, ProvidersStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SignInMethodsApiTest {

    /** {@code store.public-url} in tests. */
    private static final String STOREFRONT = "http://localhost:5173";
    private static final String IDENTITIES = "/api/account/identities";
    private static final String PASSWORD_PATH = "/api/account/password";
    private static final String LOGIN = "/api/account/login";
    private static final String PASSWORD = "ocho1234";
    private static final Account ANA_AT_GOOGLE = new Account("109876543210987654321", "ana@example.com", true,
        "Ana Pérez");
    private static final Account ANA_AT_FACEBOOK = new Account("10229876543210", "ana.perez@example.com", false,
        "Ana Pérez");

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ProvidersStub providers;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;

    SignInMethodsApiTest(TestRestTemplate http, ObjectMapper json, ProvidersStub providers, PasswordHasher passwords,
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
        owner.execute("truncate cart_item, cart, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenACustomerWhoseOnlyMethodIsGoogle_whenItUnlinksIt_thenItIsRefusedUntilItCreatesAPassword() {
        Browser ana = new Browser(http);
        assertThat(page(signInWithGoogle(ana, ANA_AT_GOOGLE))).isEqualTo("/");

        JsonNode methods = json(ana.get(IDENTITIES));
        assertThat(methods.get("hasPassword").asBoolean()).isFalse();
        assertThat(methods.get("identities")).hasSize(1);
        assertThat(methods.get("identities").get(0).get("provider").asText()).isEqualTo("GOOGLE");
        assertThat(methods.get("identities").get(0).get("email").asText()).isEqualTo("ana@example.com");
        assertThat(methods.get("identities").get(0).hasNonNull("linkedAt")).isTrue();

        ResponseEntity<String> refused = ana.delete(IDENTITIES + "/google");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(refused).get("problem").asText()).isEqualTo("LAST_SIGN_IN_METHOD");

        assertThat(ana.put(PASSWORD_PATH, "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(ana.delete(IDENTITIES + "/google").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode after = json(ana.get(IDENTITIES));
        assertThat(after.get("hasPassword").asBoolean()).isTrue();
        assertThat(after.get("identities")).isEmpty();
        assertThat(signInWithPassword("ana@example.com").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenTheSignInMethods_whenRead_thenTheyAreNeverCached() {
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> methods = ana.get(IDENTITIES);

        assertThat(methods.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(methods.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void givenACustomerWithAPassword_whenItCreatesAnother_thenItIsRefusedAndTheOldOneStillWorks() {
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> refused = ana.put(PASSWORD_PATH, "{\"password\":\"otra-clave-1\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(refused).get("problem").asText()).isEqualTo("PASSWORD_ALREADY_SET");
        assertThat(signInWithPassword("ana@example.com").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenAShortPassword_whenTheCustomerCreatesIt_thenItIsUnprocessable() {
        Browser ana = new Browser(http);
        signInWithGoogle(ana, ANA_AT_GOOGLE);

        ResponseEntity<String> refused = ana.put(PASSWORD_PATH, "{\"password\":\"corta\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(refused).get("problem").asText()).isEqualTo("PASSWORD_TOO_SHORT");
        assertThat(owner.queryForObject("select password_hash from app_user", String.class)).isNull();
    }

    @Test
    void givenNoAccountOfAProvider_whenTheCustomerUnlinksIt_thenItIsNotFound() {
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> facebook = ana.delete(IDENTITIES + "/facebook");
        ResponseEntity<String> unknown = ana.delete(IDENTITIES + "/apple");

        assertThat(facebook.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(facebook).get("problem").asText()).isEqualTo("IDENTITY_NOT_FOUND");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenASignedInCustomer_whenItLinksFacebookWithAnotherEmail_thenItIsLinkedAndStaysInTheSameSession() {
        Browser ana = signedIn("ana@example.com");
        String session = ana.cookie("SESSION");

        assertThat(ana.post("/api/account/oauth2/link/facebook", null).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> back = returnFromFacebook(ana, ANA_AT_FACEBOOK);

        assertThat(page(back)).isEqualTo("/cuenta?social=linked");
        assertThat(ana.cookie("SESSION")).as("the same session").isEqualTo(session);
        assertThat(json(ana.get("/api/account/me")).get("email").asText()).isEqualTo("ana@example.com");
        JsonNode identity = json(ana.get(IDENTITIES)).get("identities").get(0);
        assertThat(identity.get("provider").asText()).isEqualTo("FACEBOOK");
        assertThat(identity.get("email").asText()).isEqualTo("ana.perez@example.com");
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isOne();
    }

    @Test
    void givenAFacebookAccountOfAnotherCustomer_whenACustomerLinksIt_thenNothingIsLinked() {
        Browser beto = new Browser(http);
        signInWithFacebook(beto, ANA_AT_FACEBOOK);
        Browser ana = signedIn("ana@example.com");

        ana.post("/api/account/oauth2/link/facebook", null);
        ResponseEntity<String> back = returnFromFacebook(ana, ANA_AT_FACEBOOK);

        assertThat(page(back)).isEqualTo("/cuenta?social=already-linked");
        assertThat(json(ana.get(IDENTITIES)).get("identities")).isEmpty();
    }

    @Test
    void givenALinkStartedFromMiCuenta_whenTheCustomerCancelsAtTheProvider_thenItGoesBackThereAndNothingIsLinked() {
        Browser ana = signedIn("ana@example.com");
        ana.post("/api/account/oauth2/link/google", null);
        Authorization google = start(ana, "google");

        ResponseEntity<String> back = ana.get("/api/account/oauth2/code/google?error=access_denied&state="
            + google.parameters().get("state"));

        assertThat(page(back)).isEqualTo("/cuenta?social=cancelled");
        assertThat(json(ana.get(IDENTITIES)).get("identities")).isEmpty();
    }

    @Test
    void givenNoLinkAsked_whenASignedInCustomerComesBackFromAProvider_thenItIsASignInAndNothingIsLinkedToIt() {
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> back = signInWithFacebook(ana, ANA_AT_FACEBOOK);

        assertThat(page(back)).isEqualTo("/");
        assertThat(json(ana.get("/api/account/me")).get("email").asText()).isEqualTo("ana.perez@example.com");
        assertThat(identitiesOf("ana@example.com")).isZero();
    }

    @Test
    void givenALinkRequestWithoutTheCsrfToken_whenSent_thenItIsForbiddenAndTheReturnIsASignIn() {
        Browser ana = signedIn("ana@example.com");
        Map<String, String> cookies = new HashMap<>(ana.cookies());
        cookies.remove("XSRF-TOKEN");
        Browser forged = new Browser(http, cookies);

        assertThat(forged.post("/api/account/oauth2/link/facebook", null).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(page(returnFromFacebook(ana, ANA_AT_FACEBOOK))).isEqualTo("/");
        assertThat(identitiesOf("ana@example.com")).isZero();
    }

    @Test
    void givenAProviderThatIsNotEnabled_whenTheCustomerAsksToLinkIt_thenItIsNotFound() {
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> apple = ana.post("/api/account/oauth2/link/apple", null);

        assertThat(apple.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(apple).get("problem").asText()).isEqualTo("PROVIDER_NOT_ENABLED");
    }

    @Test
    void givenAVisitorWhoIsNotSignedIn_whenItUsesMiCuenta_thenItIsUnauthorized() {
        Browser guest = new Browser(http);
        guest.get("/api/health");

        assertThat(guest.get(IDENTITIES).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.put(PASSWORD_PATH, "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.post("/api/account/oauth2/link/google", null).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
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
        return returnFromFacebook(browser, account);
    }

    private ResponseEntity<String> returnFromFacebook(Browser browser, Account account) {
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

    /** A verified customer with a password, signed in with it. */
    private Browser signedIn(String email) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana Pérez', 'CUSTOMER', 0, now(), now())
            """, UUID.randomUUID(), email, passwords.hash(PASSWORD).value());
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

    private int identitiesOf(String email) {
        return owner.queryForObject("""
            select count(*) from external_identity i join app_user u on u.id = i.user_id where u.email = ?
            """, Integer.class, email);
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
