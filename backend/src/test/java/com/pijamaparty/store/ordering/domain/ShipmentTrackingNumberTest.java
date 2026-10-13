package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ShipmentTrackingNumberTest {

    @Test
    void givenANumberWithSurroundingSpaces_whenParsed_thenItIsTrimmed() {
        assertThat(ShipmentTrackingNumber.parse("  CA123456789AR ")).contains(
            new ShipmentTrackingNumber("CA123456789AR"));
    }

    @Test
    void givenABlankOrTooLongNumber_whenParsed_thenThereIsNone() {
        assertThat(ShipmentTrackingNumber.parse("   ")).isEmpty();
        assertThat(ShipmentTrackingNumber.parse("x".repeat(61))).isEmpty();
        assertThat(ShipmentTrackingNumber.parse("x".repeat(60))).isPresent();
    }

    @Test
    void givenAnUntrimmedOrEmptyValue_whenBuilt_thenItIsRefused() {
        assertThatThrownBy(() -> new ShipmentTrackingNumber(" CA1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShipmentTrackingNumber("")).isInstanceOf(IllegalArgumentException.class);
    }
}
