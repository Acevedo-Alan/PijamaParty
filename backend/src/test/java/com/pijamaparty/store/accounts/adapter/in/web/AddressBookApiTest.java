package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.platform.testing.Browser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * UC-20 (T-E4-05) over real HTTP, as the storefront sees it: a signed-in customer manages its address book. Row Level
 * Security, PostgreSQL, CSRF and Spring Session are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AddressBookApiTest {

    private static final String ADDRESSES = "/api/account/addresses";
    private static final String LOGIN = "/api/account/login";
    private static final String PASSWORD = "ocho1234";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private Browser ana;

    AddressBookApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                       PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void anaIsSignedIn() {
        ana = signedIn("ana@example.com");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox, address, user_token, external_identity, pending_link, admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenAnAddressMarkedAsPrimary_whenAnotherOneIsMarked_thenThePreviousOneStopsBeingPrimary() {
        ResponseEntity<String> first = ana.post(ADDRESSES, address("San Martín", false));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstId = json(first).get("id").asText();
        assertThat(first.getHeaders().getLocation()).hasToString(ADDRESSES + "/" + firstId);
        assertThat(json(first).get("primary").asBoolean()).as("the first one is the primary").isTrue();
        String secondId = json(ana.post(ADDRESSES, address("Rivadavia", false))).get("id").asText();

        ResponseEntity<String> marked = ana.put(ADDRESSES + "/" + secondId, address("Rivadavia", true));

        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(marked).get("primary").asBoolean()).isTrue();
        JsonNode listed = json(ana.get(ADDRESSES));
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).get("id").asText()).isEqualTo(secondId);
        assertThat(listed.get(0).get("primary").asBoolean()).isTrue();
        assertThat(listed.get(1).get("id").asText()).isEqualTo(firstId);
        assertThat(listed.get(1).get("primary").asBoolean()).isFalse();
    }

    @Test
    void givenAnAddress_whenItIsAddedAndEdited_thenItIsNormalizedAndEveryFieldIsReplaced() {
        Map<String, Object> typed = new HashMap<>(Map.of("street", " San Martín ", "number", "1234", "floor", " 3 ",
            "apartment", "", "city", "Santa Fe", "province", "SANTA_FE", "postalCode", "s3000abc"));
        JsonNode added = json(ana.post(ADDRESSES, json(typed)));
        assertThat(added.get("street").asText()).isEqualTo("San Martín");
        assertThat(added.get("floor").asText()).isEqualTo("3");
        assertThat(added.get("apartment").isNull()).isTrue();
        assertThat(added.get("postalCode").asText()).isEqualTo("S3000ABC");

        ResponseEntity<String> edited = ana.put(ADDRESSES + "/" + added.get("id").asText(), json(Map.of(
            "street", "Bv. Pellegrini", "number", "2500", "city", "Rosario", "province", "SANTA_FE",
            "postalCode", "2000", "primary", false)));

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(edited).get("floor").isNull()).isTrue();
        assertThat(json(edited).get("city").asText()).isEqualTo("Rosario");
        assertThat(json(edited).get("primary").asBoolean()).as("the only address stays primary").isTrue();
    }

    @Test
    void givenThePrimary_whenItIsDeleted_thenTheOldestRemainingOneBecomesPrimary() {
        String firstId = json(ana.post(ADDRESSES, address("San Martín", false))).get("id").asText();
        ana.post(ADDRESSES, address("Rivadavia", false));
        String newestId = json(ana.post(ADDRESSES, address("Belgrano", true))).get("id").asText();

        assertThat(ana.delete(ADDRESSES + "/" + newestId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode listed = json(ana.get(ADDRESSES));
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).get("id").asText()).isEqualTo(firstId);
        assertThat(listed.get(0).get("primary").asBoolean()).isTrue();
        assertThat(ana.delete(ADDRESSES + "/" + newestId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenACustomerWith10Addresses_whenItAddsAnother_thenItIsRefused() {
        for (int i = 0; i < AddressBook.MAX_ADDRESSES; i++) {
            assertThat(ana.post(ADDRESSES, address("Calle " + i, false)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<String> eleventh = ana.post(ADDRESSES, address("Calle 11", true));

        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(eleventh.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json(eleventh).get("problem").asText()).isEqualTo("ADDRESS_BOOK_FULL");
        assertThat(json(ana.get(ADDRESSES))).hasSize(AddressBook.MAX_ADDRESSES);
    }

    @Test
    void givenACustomerWith9Addresses_whenItAddsTwoAtOnce_thenOnlyOneFitsAndTheBookKeepsOnePrimary()
        throws Exception {
        for (int i = 0; i < AddressBook.MAX_ADDRESSES - 1; i++) {
            ana.post(ADDRESSES, address("Calle " + i, false));
        }
        Callable<HttpStatus> addOne = () ->
            HttpStatus.valueOf(ana.post(ADDRESSES, address("Concurrente", true)).getStatusCode().value());

        List<HttpStatus> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (Future<HttpStatus> status : pool.invokeAll(List.of(addOne, addOne))) {
                statuses.add(status.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(owner.queryForObject("select count(*) from address", Integer.class))
            .isEqualTo(AddressBook.MAX_ADDRESSES);
        assertThat(owner.queryForObject("select count(*) from address where is_primary", Integer.class)).isOne();
    }

    @Test
    void givenFieldsThatBreakTheRules_whenAdded_thenTheInvalidOnesAreNamed() {
        ResponseEntity<String> invalid = ana.post(ADDRESSES, json(Map.of("street", " ", "number", "1234",
            "city", "Santa Fe", "province", "SANTA_FE", "postalCode", "S3000")));

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(invalid).get("problem").asText()).isEqualTo("INVALID_ADDRESS");
        assertThat(json(invalid).get("fields")).extracting(JsonNode::asText).containsExactly("street", "postalCode");
        assertThat(invalid.getBody()).doesNotContain("S3000");

        ResponseEntity<String> unknownProvince = ana.post(ADDRESSES, json(Map.of("street", "San Martín",
            "number", "1234", "city", "Santa Fe", "province", "ATLANTIS", "postalCode", "S3000ABC")));
        assertThat(unknownProvince.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(ana.get(ADDRESSES))).isEmpty();
    }

    @Test
    void givenAnotherCustomersAddress_whenACustomerEditsOrDeletesIt_thenItIsNotFoundAndStaysAsItWas() {
        String anasId = json(ana.post(ADDRESSES, address("San Martín", false))).get("id").asText();
        Browser bruno = signedIn("bruno@example.com");

        ResponseEntity<String> edited = bruno.put(ADDRESSES + "/" + anasId, address("Rivadavia", true));
        ResponseEntity<String> deleted = bruno.delete(ADDRESSES + "/" + anasId);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(edited).get("problem").asText()).isEqualTo("ADDRESS_NOT_FOUND");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(bruno.get(ADDRESSES))).isEmpty();
        assertThat(json(ana.get(ADDRESSES)).get(0).get("street").asText()).isEqualTo("San Martín");
    }

    @Test
    void givenAVisitorWhoIsNotSignedIn_whenItUsesTheAddressBook_thenItIsUnauthorized() {
        Browser guest = new Browser(http);
        guest.get("/api/health");

        assertThat(guest.get(ADDRESSES).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.post(ADDRESSES, address("San Martín", false)).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenASignedInCustomerWithoutTheCsrfToken_whenItAddsAnAddress_thenItIsForbidden() {
        Map<String, String> cookies = new HashMap<>(ana.cookies());
        cookies.remove("XSRF-TOKEN");
        Browser forged = new Browser(http, cookies);

        assertThat(forged.post(ADDRESSES, address("San Martín", false)).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(owner.queryForObject("select count(*) from address", Integer.class)).isZero();
    }

    private Browser signedIn(String email) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana Pérez', 'CUSTOMER', 0, now(), now())
            """, UUID.randomUUID(), email, passwords.hash(PASSWORD).value());
        Browser browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post(LOGIN, json(Map.of("email", email, "password", PASSWORD))).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        return browser;
    }

    private String address(String street, boolean primary) {
        return json(Map.of("street", street, "number", "1234", "city", "Santa Fe", "province", "SANTA_FE",
            "postalCode", "S3000ABC", "primary", primary));
    }

    private String json(Map<String, ?> body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
