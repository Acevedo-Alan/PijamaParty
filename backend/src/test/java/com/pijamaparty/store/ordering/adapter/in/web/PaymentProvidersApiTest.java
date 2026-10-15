package com.pijamaparty.store.ordering.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.payments.adapter.out.modo.ModoStubConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC-13 (T-E3b-04): the payment providers of step 2 with MODO switched on, through the API. With the default
 * configuration (MODO off) see {@link CheckoutApiTest}.
 */
@ApiTest
@Import(ModoStubConfiguration.class)
class PaymentProvidersApiTest {

    private final MockMvc mvc;

    PaymentProvidersApiTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenMODOSwitchedOn_whenAVisitorAsksThePaymentProviders_thenBothComeWithMercadoPagoFirst() throws Exception {
        mvc.perform(get("/api/checkout/payment-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providers.length()").value(2))
            .andExpect(jsonPath("$.providers[0]").value("MERCADOPAGO"))
            .andExpect(jsonPath("$.providers[1]").value("MODO"));
    }
}
