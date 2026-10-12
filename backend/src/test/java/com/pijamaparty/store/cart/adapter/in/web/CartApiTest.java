package com.pijamaparty.store.cart.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.security.ActorResolver;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-08 and UC-09 through the cart API, with the real security filter chain, Row Level Security and PostgreSQL. The
 * {@code cart_token} cookie arrives in T-E2-04, so a test-only resolver makes the request a guest with the token of
 * the {@value #TEST_CART_TOKEN} header.
 */
@ApiTest
@Import(CartApiTest.GuestByHeader.class)
class CartApiTest {

    static final String TEST_CART_TOKEN = "X-Test-Cart-Token";
    private static final String ITEMS = "/api/cart/items";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private final String token = "guest-" + UUID.randomUUID();
    private UUID variant;

    CartApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A published pijama of an active category at ARS 15,000 with 3 units available. */
    @BeforeEach
    void sellVariant() {
        variant = sellable(1_500_000, 3);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart, stock_movement, stock_level, product_image, variant, product, category");
    }

    @Test
    void givenAVisitorWithoutACart_whenAddingTheFirstProduct_thenTheCartIsCreatedAndReturned() throws Exception {
        add(variant, 2)
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].variantId").value(variant.toString()))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPriceCents").value(1_500_000));

        mvc.perform(guest(get("/api/cart")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(get("/api/cart").header(TEST_CART_TOKEN, "another-guest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenAVariantWithStock3And2UnitsInTheCart_whenAdding2More_thenItIsRejectedWithTheUnitsLeft() throws Exception {
        add(variant, 2).andExpect(status().isOk());

        add(variant, 2)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.variantId").value(variant.toString()))
            .andExpect(jsonPath("$.available").value(3));

        mvc.perform(guest(get("/api/cart"))).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void givenAVariantInTheCart_whenAddingItAgain_thenItsLineAddsTheQuantity() throws Exception {
        add(variant, 1).andExpect(status().isOk());

        add(variant, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void givenAnUnknownOrHiddenVariant_whenAddingIt_thenItIsNotFound() throws Exception {
        add(UUID.randomUUID(), 1)
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        owner.update("update product set active = false");
        add(variant, 1).andExpect(status().isNotFound());
    }

    @Test
    void givenAnInvalidBody_whenAdding_thenItIsABadRequest() throws Exception {
        add(variant, 0)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        add(variant, 1_000_001).andExpect(status().isBadRequest());
        mvc.perform(guest(post(ITEMS)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void givenAVisitorWithoutATokenOrTheAdministrator_whenAdding_thenItIsForbiddenAndTheirCartIsEmpty()
        throws Exception {
        String body = json.writeValueAsString(Map.of("variantId", variant, "quantity", 1));

        mvc.perform(post(ITEMS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(ITEMS).with(csrf()).with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/cart"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenNoCsrfToken_whenAdding_thenItIsRejected() throws Exception {
        mvc.perform(guest(post(ITEMS)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", 1))))
            .andExpect(status().isForbidden());
    }

    @Test
    void givenAnItemInTheCart_whenItsQuantityIsChangedTo0_thenItIsRemoved() throws Exception {
        String item = itemIdOf(add(variant, 2));

        changeQuantity(item, 0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenAnItem_whenChangingItsQuantity_thenTheStockIsRevalidated() throws Exception {
        String item = itemIdOf(add(variant, 1));

        changeQuantity(item, 3)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(3));
        changeQuantity(item, 4)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.available").value(3));
        changeQuantity(item, -1).andExpect(status().isBadRequest());
    }

    @Test
    void givenAVariantThatSoldOutWhileInTheCart_whenChangingItsQuantity_thenOnlyRemovingItWorks() throws Exception {
        String item = itemIdOf(add(variant, 2));
        owner.update("update stock_level set on_hand = 0");

        changeQuantity(item, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.available").value(0));
        changeQuantity(item, 0).andExpect(status().isOk());
    }

    @Test
    void givenAnItem_whenRemovingIt_thenTheFullCartNoLongerHasIt() throws Exception {
        UUID other = sellable(990_000, 5);
        String item = itemIdOf(add(variant, 1));
        add(other, 1).andExpect(status().isOk());

        mvc.perform(guest(delete(ITEMS + "/" + item)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].variantId").value(other.toString()));
    }

    @Test
    void givenAnItemOfAnotherGuest_whenChangingOrRemovingIt_thenItIsNotFound() throws Exception {
        String item = itemIdOf(add(variant, 1));

        mvc.perform(patch(ITEMS + "/" + item).header(TEST_CART_TOKEN, "another-guest").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(delete(ITEMS + "/" + item).header(TEST_CART_TOKEN, "another-guest").with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(guest(get("/api/cart"))).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    private UUID sellable(long priceCents, int available) {
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
            values (?, ?, ?, 'M', 0, 'Rosa', 0, ?, true, now(), now())
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8), priceCents);
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            available);
        return variantId;
    }

    private ResultActions add(UUID variantId, int quantity) throws Exception {
        return mvc.perform(guest(post(ITEMS)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("variantId", variantId, "quantity", quantity))));
    }

    private ResultActions changeQuantity(String item, int quantity) throws Exception {
        return mvc.perform(guest(patch(ITEMS + "/" + item)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("quantity", quantity))));
    }

    private String itemIdOf(ResultActions response) throws Exception {
        JsonNode body = json.readTree(response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.path("items").path(0).path("id").asText();
    }

    private MockHttpServletRequestBuilder guest(MockHttpServletRequestBuilder request) {
        return request.header(TEST_CART_TOKEN, token);
    }

    /** Stands in for the {@code cart_token} cookie of T-E2-04. */
    @TestConfiguration(proxyBeanMethods = false)
    static class GuestByHeader {

        @Bean
        ActorResolver guestByTestHeader() {
            return request -> Optional.ofNullable(request.getHeader(TEST_CART_TOKEN))
                .map(token -> Actor.anonymous(Optional.of(new CartToken(token)), Optional.empty()));
        }
    }
}
