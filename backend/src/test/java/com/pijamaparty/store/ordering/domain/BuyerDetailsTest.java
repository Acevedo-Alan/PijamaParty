package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuyerDetailsTest {

    @Test
    void givenABuyer_whenPrinted_thenItsPersonalDataIsHidden() {
        var buyer = new BuyerDetails("Ana", "Pérez", "ana@example.com", "3425551234", "12345678");

        assertThat(buyer.toString()).doesNotContain("Ana", "Pérez", "ana@example.com", "3425551234", "12345678");
    }
}
