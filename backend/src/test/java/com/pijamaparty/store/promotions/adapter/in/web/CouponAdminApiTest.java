package com.pijamaparty.store.promotions.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/** UC-11 through the back office API, with the real security filter chain and PostgreSQL. */
@ApiTest
class CouponAdminApiTest {

    private static final String COUPONS = "/api/admin/coupons";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;

    CouponAdminApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate coupon_scope, coupon_redemption, coupon");
    }

    @Test
    void givenEveryField_whenTheAdministratorCreatesACoupon_thenItIsActiveWithoutUses() throws Exception {
        send(post(COUPONS), """
            {"code":" verano-10 ","type":"FIXED_AMOUNT","discountValue":500000,"validFrom":"2026-12-01",
             "validUntil":"2027-02-28","maxUses":100,"minimumPurchaseCents":2000000}
            """)
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("VERANO-10"))
            .andExpect(jsonPath("$.type").value("FIXED_AMOUNT"))
            .andExpect(jsonPath("$.discountValue").value(500_000))
            .andExpect(jsonPath("$.validFrom").value("2026-12-01"))
            .andExpect(jsonPath("$.validUntil").value("2027-02-28"))
            .andExpect(jsonPath("$.maxUses").value(100))
            .andExpect(jsonPath("$.minimumPurchaseCents").value(2_000_000))
            .andExpect(jsonPath("$.automatic").value(false))
            .andExpect(jsonPath("$.scope.productIds", hasSize(0)))
            .andExpect(jsonPath("$.scope.categoryIds", hasSize(0)))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.uses").value(0));

        create("PIJAMA15")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.validFrom").isEmpty())
            .andExpect(jsonPath("$.validUntil").isEmpty())
            .andExpect(jsonPath("$.maxUses").isEmpty())
            .andExpect(jsonPath("$.minimumPurchaseCents").isEmpty());
    }

    @Test
    void givenAnAutomaticCoupon_whenCreatedWithAScopeOfOneCategory_thenTheListShowsItAsAutomaticWithThatCategory()
        throws Exception {
        UUID category = UUID.randomUUID();

        send(post(COUPONS), """
            {"code":"VERANO10","type":"PERCENTAGE","discountValue":10,"automatic":true,
             "scope":{"categoryIds":["%s"]}}
            """.formatted(category))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.automatic").value(true));

        mvc.perform(get(COUPONS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("VERANO10"))
            .andExpect(jsonPath("$[0].automatic").value(true))
            .andExpect(jsonPath("$[0].scope.productIds", hasSize(0)))
            .andExpect(jsonPath("$[0].scope.categoryIds", contains(category.toString())));
    }

    @Test
    void givenACodeCoupon_whenAScopeIsSentForIt_thenItIsRefused() throws Exception {
        String scope = "\"scope\":{\"productIds\":[\"" + UUID.randomUUID() + "\"]}";

        send(post(COUPONS), "{\"code\":\"VERANO10\",\"type\":\"PERCENTAGE\",\"discountValue\":10," + scope + "}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.fields", contains("SCOPE")));
        String id = idOf(create("INVIERNO"));
        send(put(COUPONS + "/" + id), "{\"code\":\"INVIERNO\",\"type\":\"PERCENTAGE\",\"discountValue\":10,"
            + "\"automatic\":true," + scope + "}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("SCOPE")));
        send(post(COUPONS), "{\"code\":\"OTONO\",\"type\":\"PERCENTAGE\",\"discountValue\":10,"
            + "\"scope\":{\"productIds\":[null]}}")
            .andExpect(status().isBadRequest());
        send(post(COUPONS), "{\"code\":\"OTONO\",\"type\":\"PERCENTAGE\",\"discountValue\":10,\"automatic\":true,"
            + "\"scope\":{\"categoryIds\":" + json.writeValueAsString(Stream.generate(UUID::randomUUID).limit(101)
            .toList()) + "}}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("SCOPE")));
        send(post(COUPONS), "{\"code\":\"OTONO\",\"type\":\"PERCENTAGE\",\"discountValue\":10,\"automatic\":true,"
            + "\"scope\":{\"categoryIds\":" + json.writeValueAsString(Stream.generate(UUID::randomUUID).limit(401)
            .toList()) + "}}")
            .andExpect(status().isBadRequest());
        mvc.perform(get(COUPONS).with(ADMIN))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].automatic").value(false));
    }

    @Test
    void givenAnAutomaticCoupon_whenEditedWithoutAutomatic_thenItStaysAutomaticWithTheNewScope() throws Exception {
        String id = idOf(send(post(COUPONS), """
            {"code":"VERANO10","type":"PERCENTAGE","discountValue":10,"automatic":true,
             "scope":{"categoryIds":["%s"]}}
            """.formatted(UUID.randomUUID())));
        UUID product = UUID.randomUUID();

        send(put(COUPONS + "/" + id), """
            {"code":"VERANO10","type":"PERCENTAGE","discountValue":15,"scope":{"productIds":["%s","%s"]}}
            """.formatted(product, product))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.automatic").value(true))
            .andExpect(jsonPath("$.discountValue").value(15))
            .andExpect(jsonPath("$.scope.productIds", contains(product.toString())))
            .andExpect(jsonPath("$.scope.categoryIds", hasSize(0)));
    }

    @Test
    void givenAnExistingCode_whenAnotherWithTheSameCodeIsCreated_thenItIsRejected() throws Exception {
        create("VERANO10").andExpect(status().isCreated());

        create("verano10")
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Code already used"))
            .andExpect(jsonPath("$.code").value("VERANO10"));
        mvc.perform(get(COUPONS).with(ADMIN)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void givenACouponWithRecordedUses_whenDeletionIsAttempted_thenOnlyDeactivatingItIsAllowed() throws Exception {
        String id = idOf(create("VERANO10"));
        givenRedemptions(id, 3);

        mvc.perform(delete(COUPONS + "/" + id).with(ADMIN).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Coupon has uses"))
            .andExpect(jsonPath("$.detail").value("A coupon with uses cannot be deleted; deactivate it instead."));

        mvc.perform(post(COUPONS + "/" + id + "/deactivate").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.uses").value(3));
        mvc.perform(get(COUPONS).with(ADMIN))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].active").value(false))
            .andExpect(jsonPath("$[0].uses").value(3));
    }

    @Test
    void givenACouponWithoutUses_whenDeleted_thenItIsGone() throws Exception {
        String id = idOf(create("VERANO10"));

        mvc.perform(delete(COUPONS + "/" + id).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(delete(COUPONS + "/" + id).with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(COUPONS).with(ADMIN)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void givenSeveralCoupons_whenListed_thenTheNewestComesFirstWithItsUses() throws Exception {
        String first = idOf(create("PRIMERO"));
        String second = idOf(create("SEGUNDO"));
        givenRedemptions(first, 2);

        mvc.perform(get(COUPONS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(second, first)))
            .andExpect(jsonPath("$[*].uses", contains(0, 2)));
    }

    @Test
    void givenAUsedCoupon_whenEveryFieldIsEdited_thenItKeepsItsStateAndUsesUnlessTheCodeIsTaken() throws Exception {
        create("VERANO10");
        String id = idOf(create("INVIERNO"));
        givenRedemptions(id, 1);
        mvc.perform(post(COUPONS + "/" + id + "/deactivate").with(ADMIN).with(csrf())).andExpect(status().isOk());

        send(put(COUPONS + "/" + id), """
            {"code":"otono","type":"FIXED_AMOUNT","discountValue":300000,"validUntil":"2027-05-31","maxUses":10}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OTONO"))
            .andExpect(jsonPath("$.type").value("FIXED_AMOUNT"))
            .andExpect(jsonPath("$.discountValue").value(300_000))
            .andExpect(jsonPath("$.validUntil").value("2027-05-31"))
            .andExpect(jsonPath("$.maxUses").value(10))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.uses").value(1));
        send(put(COUPONS + "/" + id), coupon("verano10"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERANO10"));
        send(put(COUPONS + "/" + UUID.randomUUID()), coupon("PRIMAVERA")).andExpect(status().isNotFound());
    }

    @Test
    void givenADeactivatedCoupon_whenActivated_thenItIsActiveAgain() throws Exception {
        String id = idOf(create("VERANO10"));
        mvc.perform(post(COUPONS + "/" + id + "/deactivate").with(ADMIN).with(csrf())).andExpect(status().isOk());

        mvc.perform(post(COUPONS + "/" + id + "/activate").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post(COUPONS + "/" + UUID.randomUUID() + "/activate").with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void givenInvalidData_whenCreating_thenTheInvalidFieldsAreNamedAsProblemJson() throws Exception {
        send(post(COUPONS), """
            {"code":"AB","type":"PERCENTAGE","discountValue":101,"validFrom":"2026-12-02",
             "validUntil":"2026-12-01","maxUses":0,"minimumPurchaseCents":0}
            """)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid coupon"))
            .andExpect(jsonPath("$.fields",
                contains("CODE", "DISCOUNT_VALUE", "MAX_USES", "MINIMUM_PURCHASE", "VALIDITY")));

        send(post(COUPONS), "{}").andExpect(status().isBadRequest());
        send(post(COUPONS), "{\"code\":\"VERANO10\",\"type\":\"GIFT\",\"discountValue\":10}")
            .andExpect(status().isBadRequest());
        send(post(COUPONS), "{\"code\":\"VERANO10\",\"type\":\"PERCENTAGE\",\"discountValue\":10,"
            + "\"validFrom\":\"1 de diciembre\"}").andExpect(status().isBadRequest());
        create("A".repeat(101)).andExpect(status().isBadRequest());
        mvc.perform(get(COUPONS).with(ADMIN)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenManagingCoupons_thenAccessIsDenied() throws Exception {
        mvc.perform(get(COUPONS)).andExpect(status().isUnauthorized());
        mvc.perform(get(COUPONS).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(COUPONS).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(coupon("VERANO10")))
            .andExpect(status().isForbidden());
        mvc.perform(get(COUPONS).with(ADMIN)).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions create(String code) throws Exception {
        return send(post(COUPONS), coupon(code));
    }

    private String coupon(String code) throws Exception {
        return "{\"code\":" + json.writeValueAsString(code) + ",\"type\":\"PERCENTAGE\",\"discountValue\":10}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String idOf(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    /** {@code orders} confirmed orders used the coupon; no endpoint records them before E3. */
    private void givenRedemptions(String coupon, int orders) {
        for (int i = 0; i < orders; i++) {
            owner.update("insert into coupon_redemption (coupon_id, order_id, redeemed_at) values (?, ?, now())",
                UUID.fromString(coupon), UUID.randomUUID());
        }
    }
}
