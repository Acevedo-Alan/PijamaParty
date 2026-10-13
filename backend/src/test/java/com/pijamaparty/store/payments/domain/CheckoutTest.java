package com.pijamaparty.store.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CheckoutTest {

    private static final URI URL = URI.create("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=1");

    @Test
    void givenAnIdAndAnHttpsUrl_whenBuildingTheCheckout_thenItIsValid() {
        assertThat(new Checkout("123-pref", URL).url()).isEqualTo(URL);
    }

    @Test
    void givenAPlainHttpUrl_whenBuildingTheCheckout_thenItIsRejected() {
        assertThatThrownBy(() -> new Checkout("123-pref", URI.create("http://www.mercadopago.com.ar/checkout")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenARelativeUrl_whenBuildingTheCheckout_thenItIsRejected() {
        assertThatThrownBy(() -> new Checkout("123-pref", URI.create("/checkout")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenABlankOrTooLongId_whenBuildingTheCheckout_thenItIsRejected() {
        assertThatThrownBy(() -> new Checkout(" ", URL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Checkout("x".repeat(Checkout.MAX_ID_LENGTH + 1), URL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAUrlLongerThanTheColumn_whenBuildingTheCheckout_thenItIsRejected() {
        URI longUrl = URI.create("https://pay.example.com/" + "x".repeat(Checkout.MAX_URL_LENGTH));

        assertThatThrownBy(() -> new Checkout("123-pref", longUrl)).isInstanceOf(IllegalArgumentException.class);
    }
}
