package com.pijamaparty.store.cart.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-08, UC-09, UC-10 and the {@code __Host-cart_token} cookie of T-E2-04 through the cart API, with the real security
 * filter chain, Row Level Security and PostgreSQL. The guest of most tests already holds a cookie.
 */
@ApiTest
class CartApiTest {

    private static final String ITEMS = "/api/cart/items";
    private static final String COUPON = "/api/cart/coupon";
    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private final String token = newToken();
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
        owner.execute("truncate cart_item, cart, coupon_scope, coupon_redemption, coupon, stock_movement, stock_level, "
            + "product_image, variant, product, size_guide, category");
    }

    @Test
    void givenAVisitorWithoutACart_whenAddingTheFirstProduct_thenTheCartIsCreatedAndReturned() throws Exception {
        add(variant, 2)
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].variantId").value(variant.toString()))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPriceCents").value(1_500_000))
            .andExpect(jsonPath("$.items[0].previousUnitPriceCents").isEmpty())
            .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
            .andExpect(jsonPath("$.items[0].available").value(3))
            .andExpect(jsonPath("$.subtotalCents").value(3_000_000))
            .andExpect(jsonPath("$.discountCents").value(0))
            .andExpect(jsonPath("$.totalCents").value(3_000_000));

        mvc.perform(guest(get("/api/cart")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(get("/api/cart").cookie(cartToken(newToken())))
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
    void givenTheAdministrator_whenAdding_thenItIsForbiddenEvenWithACartCookie() throws Exception {
        mvc.perform(guest(post(ITEMS)).with(csrf()).with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", 1))))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(cookie().doesNotExist(CART_COOKIE));
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isZero();
    }

    @Test
    void givenAVisitorWithoutACookie_whenAddingTheFirstProduct_thenACartTokenCookieTiesTheCartToTheBrowser()
        throws Exception {
        MockHttpServletResponse response = mvc.perform(post(ITEMS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andReturn().getResponse();

        Cookie issued = response.getCookie(CART_COOKIE);
        assertThat(issued).isNotNull();
        assertThat(issued.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.isHttpOnly()).isTrue();
        assertThat(issued.getSecure()).isTrue();
        assertThat(((MockCookie) issued).getSameSite()).isEqualTo("Lax");
        assertThat(issued.getPath()).isEqualTo("/");
        assertThat(issued.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        // RNF-29.6: the database only knows the hash of the token.
        assertThat(owner.queryForList("select token_hash from cart", String.class))
            .containsExactly(new CartToken(issued.getValue()).hash());

        mvc.perform(get("/api/cart").cookie(cartToken(issued.getValue())))
            .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(get("/api/cart")).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenAVisitorWithoutACookie_whenTheFirstAddIsRejected_thenNoCookieAndNoCartAreCreated() throws Exception {
        mvc.perform(post(ITEMS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", 4))))
            .andExpect(status().isConflict())
            .andExpect(cookie().doesNotExist(CART_COOKIE));
        mvc.perform(post(ITEMS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", UUID.randomUUID(), "quantity", 1))))
            .andExpect(status().isNotFound())
            .andExpect(cookie().doesNotExist(CART_COOKIE));
        mvc.perform(post(ITEMS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(cookie().doesNotExist(CART_COOKIE));

        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isZero();
    }

    @Test
    void givenAMalformedCookie_whenAdding_thenItIsIgnoredAndANewTokenIsIssued() throws Exception {
        String issued = mvc.perform(post(ITEMS).cookie(cartToken("not-a-token")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", 1))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getCookie(CART_COOKIE).getValue();

        assertThat(issued).isNotEqualTo("not-a-token").matches("[A-Za-z0-9_-]{43}");
        mvc.perform(get("/api/cart").cookie(cartToken("not-a-token"))).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenATokenInACookieWithoutTheHostPrefix_whenReadingTheCart_thenItIsIgnored() throws Exception {
        add(variant, 1).andExpect(status().isOk());

        mvc.perform(get("/api/cart").cookie(new Cookie("cart_token", token)))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenAGuestWithACookie_whenChangingTheCart_thenTheSameCookieIsSetAgainButNotWhenReadingIt() throws Exception {
        add(variant, 1).andExpect(cookie().value(CART_COOKIE, token));
        String item = itemIdOf(add(variant, 1));
        changeQuantity(item, 1).andExpect(cookie().value(CART_COOKIE, token));
        mvc.perform(guest(post("/api/cart/acknowledge-prices")).with(csrf()))
            .andExpect(cookie().value(CART_COOKIE, token));

        mvc.perform(guest(get("/api/cart"))).andExpect(cookie().doesNotExist(CART_COOKIE));
        mvc.perform(guest(delete(ITEMS + "/" + item)).with(csrf())).andExpect(cookie().value(CART_COOKIE, token));
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isEqualTo(1);
    }

    @Test
    void givenAVisitorWithoutACookie_whenChangingTheCart_thenItIsForbiddenAndTheCartIsEmpty() throws Exception {
        mvc.perform(patch(ITEMS + "/" + UUID.randomUUID()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}"))
            .andExpect(status().isForbidden())
            .andExpect(cookie().doesNotExist(CART_COOKIE));
        mvc.perform(delete(ITEMS + "/" + UUID.randomUUID()).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(cookie().doesNotExist(CART_COOKIE));
        mvc.perform(post("/api/cart/acknowledge-prices").with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(cookie().doesNotExist(CART_COOKIE));
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
    void givenAnItem_whenReadingTheCart_thenItShowsItsProductSizeColorAndPhotoAlsoOnceTheShopStopsSellingIt()
        throws Exception {
        owner.update("""
            insert into product_image (id, product_id, color, storage_key, placeholder, position, upload_key,
                                       content_sha256, created_at)
            select ?, v.product_id, 'Rosa', 'luna-rosa', 'data:image/webp;base64,UklGRg==', 0, ?, ?, now()
            from variant v
            where v.id = ?
            """, UUID.randomUUID(), UUID.randomUUID(), new byte[32], variant);

        add(variant, 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].product.name").value("Pijama"))
            .andExpect(jsonPath("$.items[0].product.slug").value(startsWith("pijama-")))
            .andExpect(jsonPath("$.items[0].product.sku").value(startsWith("PP-")))
            .andExpect(jsonPath("$.items[0].product.size").value("M"))
            .andExpect(jsonPath("$.items[0].product.color").value("Rosa"))
            .andExpect(jsonPath("$.items[0].product.image.placeholder").value("data:image/webp;base64,UklGRg=="))
            .andExpect(jsonPath("$.items[0].product.image.files[*].width").value(contains(400, 800, 1200)))
            .andExpect(jsonPath("$.items[0].product.image.files[0].url").value("/uploads/luna-rosa-400.webp"));
        owner.update("update product set active = false");

        mvc.perform(guest(get("/api/cart")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.items[0].product.name").value("Pijama"));
    }

    @Test
    void givenAVariantThatSoldOutWhileInTheCart_whenTheCartOpens_thenItIsShownAsUnavailable() throws Exception {
        UUID other = sellable(990_000, 5);
        add(variant, 2).andExpect(status().isOk());
        add(other, 1).andExpect(status().isOk());
        owner.update("update stock_level set on_hand = 0 where variant_id = ?", variant);

        mvc.perform(guest(get("/api/cart")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.items[0].available").value(0))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[1].status").value("AVAILABLE"))
            .andExpect(jsonPath("$.totalCents").value(990_000));
    }

    @Test
    void givenFewerUnitsLeftThanInTheCart_whenTheCartOpens_thenTheItemShowsTheUnitsLeft() throws Exception {
        add(variant, 3).andExpect(status().isOk());
        owner.update("update stock_level set on_hand = 1");

        mvc.perform(guest(get("/api/cart")))
            .andExpect(jsonPath("$.items[0].status").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.items[0].available").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(3))
            .andExpect(jsonPath("$.totalCents").value(0));
    }

    @Test
    void givenAPriceChangeInTheCatalog_whenTheCartOpens_thenTheTotalUsesTheNewPriceUntilTheChangeIsAcknowledged()
        throws Exception {
        add(variant, 2).andExpect(status().isOk());
        owner.update("update variant set price_cents = 1600000");

        mvc.perform(guest(get("/api/cart")))
            .andExpect(jsonPath("$.items[0].unitPriceCents").value(1_600_000))
            .andExpect(jsonPath("$.items[0].previousUnitPriceCents").value(1_500_000))
            .andExpect(jsonPath("$.totalCents").value(3_200_000));
        mvc.perform(guest(get("/api/cart")))
            .andExpect(jsonPath("$.items[0].previousUnitPriceCents").value(1_500_000));

        mvc.perform(guest(post("/api/cart/acknowledge-prices")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].unitPriceCents").value(1_600_000))
            .andExpect(jsonPath("$.items[0].previousUnitPriceCents").isEmpty())
            .andExpect(jsonPath("$.totalCents").value(3_200_000));
        mvc.perform(guest(get("/api/cart")))
            .andExpect(jsonPath("$.items[0].previousUnitPriceCents").isEmpty());
    }

    @Test
    void givenTheAdministratorOrNoCsrfToken_whenAcknowledgingPrices_thenItIsForbidden() throws Exception {
        mvc.perform(post("/api/cart/acknowledge-prices").with(csrf())
                .with(user(UUID.randomUUID().toString()).roles("ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(guest(post("/api/cart/acknowledge-prices"))).andExpect(status().isForbidden());
    }

    @Test
    void givenAnItemOfAnotherGuest_whenChangingOrRemovingIt_thenItIsNotFound() throws Exception {
        String item = itemIdOf(add(variant, 1));

        mvc.perform(patch(ITEMS + "/" + item).cookie(cartToken(newToken())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(delete(ITEMS + "/" + item).cookie(cartToken(newToken())).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(guest(get("/api/cart"))).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void givenAValid10PercentCoupon_whenAppliedToA20000Cart_thenTheDiscountIs2000() throws Exception {
        add(sellable(2_000_000, 5), 1).andExpect(status().isOk());
        coupon("VERANO10", null, null, null);

        applyCoupon(" verano10 ")
            .andExpect(status().isOk())
            .andExpect(cookie().value(CART_COOKIE, token))
            .andExpect(jsonPath("$.coupon.code").value("VERANO10"))
            .andExpect(jsonPath("$.coupon.status").value("APPLICABLE"))
            .andExpect(jsonPath("$.coupon.missingCents").value(0))
            .andExpect(jsonPath("$.appliedDiscount.code").value("VERANO10"))
            .andExpect(jsonPath("$.appliedDiscount.automatic").value(false))
            .andExpect(jsonPath("$.subtotalCents").value(2_000_000))
            .andExpect(jsonPath("$.discountCents").value(200_000))
            .andExpect(jsonPath("$.totalCents").value(1_800_000));

        mvc.perform(guest(get("/api/cart")))
            .andExpect(jsonPath("$.coupon.code").value("VERANO10"))
            .andExpect(jsonPath("$.discountCents").value(200_000))
            .andExpect(jsonPath("$.totalCents").value(1_800_000));
    }

    @Test
    void givenAnAutomaticCouponThatDiscountsMore_whenACodeIsApplied_thenTheCodeIsHeldAndTheAutomaticOneDiscounts()
        throws Exception {
        add(sellable(2_000_000, 5), 1).andExpect(status().isOk());
        coupon("VERANO10", null, null, null);
        owner.update("""
            insert into coupon (id, code, type, discount_value, automatic, active, created_at, updated_at)
            values (?, 'OTONO20', 'PERCENTAGE', 20, true, true, now(), now())
            """, UUID.randomUUID());

        mvc.perform(guest(get("/api/cart")))
            .andExpect(jsonPath("$.coupon").isEmpty())
            .andExpect(jsonPath("$.appliedDiscount.code").value("OTONO20"))
            .andExpect(jsonPath("$.appliedDiscount.automatic").value(true))
            .andExpect(jsonPath("$.discountCents").value(400_000));
        applyCoupon("VERANO10")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coupon.code").value("VERANO10"))
            .andExpect(jsonPath("$.coupon.status").value("APPLICABLE"))
            .andExpect(jsonPath("$.appliedDiscount.code").value("OTONO20"))
            .andExpect(jsonPath("$.discountCents").value(400_000))
            .andExpect(jsonPath("$.totalCents").value(1_600_000));
        applyCoupon("OTONO20")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("COUPON_UNKNOWN"));
    }

    @Test
    void givenACouponAlreadyApplied_whenAnotherIsApplied_thenItReplacesIt() throws Exception {
        add(sellable(2_000_000, 5), 1).andExpect(status().isOk());
        coupon("VERANO10", null, null, null);
        coupon("PIJAMA25", null, null, null);
        owner.update("update coupon set discount_value = 25 where code = 'PIJAMA25'");
        applyCoupon("VERANO10").andExpect(status().isOk());

        applyCoupon("PIJAMA25")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coupon.code").value("PIJAMA25"))
            .andExpect(jsonPath("$.discountCents").value(500_000));
    }

    @Test
    void givenAnExpiredCoupon_whenApplied_thenItIsRejectedAndTheCartKeepsItsCoupon() throws Exception {
        add(variant, 1).andExpect(status().isOk());
        coupon("VERANO10", null, null, null);
        coupon("VENCIDO", LocalDate.of(2020, 1, 31), null, null);
        applyCoupon("VERANO10").andExpect(status().isOk());

        applyCoupon("VENCIDO")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("COUPON_EXPIRED"))
            .andExpect(cookie().doesNotExist(CART_COOKIE));

        mvc.perform(guest(get("/api/cart"))).andExpect(jsonPath("$.coupon.code").value("VERANO10"));
    }

    @Test
    void givenACartBelowTheMinimum_whenTheCouponIsApplied_thenTheUserIsToldHowMuchIsMissing() throws Exception {
        add(variant, 1).andExpect(status().isOk());
        coupon("MINIMO", null, null, 2_000_000L);

        applyCoupon("MINIMO")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("COUPON_BELOW_MINIMUM"))
            .andExpect(jsonPath("$.missingCents").value(500_000));
    }

    @Test
    void givenAUsedUpCoupon_whenApplied_thenItIsRejected() throws Exception {
        add(variant, 1).andExpect(status().isOk());
        UUID agotado = coupon("AGOTADO", null, 1, null);
        owner.update("insert into coupon_redemption (coupon_id, order_id, redeemed_at) values (?, ?, now())",
            agotado, UUID.randomUUID());

        applyCoupon("AGOTADO")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("COUPON_USED_UP"));
    }

    @Test
    void givenAnUnknownCodeOrAnInactiveCoupon_whenApplied_thenEachIsRejectedWithItsReason() throws Exception {
        add(variant, 1).andExpect(status().isOk());
        coupon("APAGADO", null, null, null);
        owner.update("update coupon set active = false");

        applyCoupon("NO-EXISTE")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("COUPON_UNKNOWN"));
        applyCoupon("APAGADO")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("COUPON_INACTIVE"));
    }

    @Test
    void givenNoCartOrAnInvalidBodyOrAnActorWithoutACart_whenApplyingACoupon_thenItIsRejected() throws Exception {
        coupon("VERANO10", null, null, null);

        applyCoupon("VERANO10")
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("EMPTY_CART"));
        applyCoupon("A".repeat(101)).andExpect(status().isBadRequest());
        mvc.perform(guest(post(COUPON)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(COUPON).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "VERANO10"))))
            .andExpect(status().isForbidden())
            .andExpect(cookie().doesNotExist(CART_COOKIE));
        mvc.perform(post(COUPON).with(csrf()).with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", "VERANO10"))))
            .andExpect(status().isForbidden());
        mvc.perform(guest(post(COUPON)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "VERANO10"))))
            .andExpect(status().isForbidden());
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isZero();
    }

    @Test
    void givenAnAppliedCoupon_whenRemovingIt_thenTheCartHasNoCouponOrDiscount() throws Exception {
        add(variant, 1).andExpect(status().isOk());
        coupon("VERANO10", null, null, null);
        applyCoupon("VERANO10").andExpect(status().isOk());

        mvc.perform(guest(delete(COUPON)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(cookie().value(CART_COOKIE, token))
            .andExpect(jsonPath("$.coupon").isEmpty())
            .andExpect(jsonPath("$.discountCents").value(0))
            .andExpect(jsonPath("$.totalCents").value(1_500_000));
        mvc.perform(guest(get("/api/cart"))).andExpect(jsonPath("$.coupon").isEmpty());
        mvc.perform(delete(COUPON).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void givenAnAppliedCouponWithAMinimum_whenTheCartFallsBelowIt_thenItStaysWithoutDiscount() throws Exception {
        String item = itemIdOf(add(variant, 2));
        coupon("MINIMO", null, null, 3_000_000L);
        applyCoupon("MINIMO").andExpect(status().isOk()).andExpect(jsonPath("$.discountCents").value(300_000));

        changeQuantity(item, 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coupon.code").value("MINIMO"))
            .andExpect(jsonPath("$.coupon.status").value("BELOW_MINIMUM"))
            .andExpect(jsonPath("$.coupon.missingCents").value(1_500_000))
            .andExpect(jsonPath("$.discountCents").value(0))
            .andExpect(jsonPath("$.totalCents").value(1_500_000));
        changeQuantity(item, 2)
            .andExpect(jsonPath("$.coupon.status").value("APPLICABLE"))
            .andExpect(jsonPath("$.discountCents").value(300_000));
    }

    /** A 10% coupon; {@code validUntil}, {@code maxUses} and {@code minimumCents} null when not limited. */
    private UUID coupon(String code, LocalDate validUntil, Integer maxUses, Long minimumCents) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, valid_until, max_uses, minimum_purchase_cents, active,
                                created_at, updated_at)
            values (?, ?, 'PERCENTAGE', 10, ?, ?, ?, true, now(), now())
            """, id, code, new SqlParameterValue(Types.DATE, validUntil), new SqlParameterValue(Types.INTEGER, maxUses),
            new SqlParameterValue(Types.BIGINT, minimumCents));
        return id;
    }

    private ResultActions applyCoupon(String code) throws Exception {
        return mvc.perform(guest(post(COUPON)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", code))));
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
        return request.cookie(cartToken(token));
    }

    private static Cookie cartToken(String value) {
        return new Cookie(CART_COOKIE, value);
    }

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
