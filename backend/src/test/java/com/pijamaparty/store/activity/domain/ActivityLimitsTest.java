package com.pijamaparty.store.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActivityLimitsTest {

    @Test
    void givenATextOf200Characters_whenLimited_thenItIsKeptWhole() {
        String text = "a".repeat(200);

        assertThat(ActivityLimits.text(text)).isEqualTo(text);
    }

    @Test
    void givenATextOf201Characters_whenLimited_thenItIsCutTo200WithAnEllipsis() {
        String limited = ActivityLimits.text("a".repeat(201));

        assertThat(limited).hasSize(200).endsWith("a…").startsWith("a".repeat(199));
    }

    @Test
    void givenATextWithCharactersOutsideTheBasicPlane_whenLimited_thenNoneIsSplitInHalf() {
        String limited = ActivityLimits.text("😀".repeat(250));

        assertThat(limited.codePointCount(0, limited.length())).isEqualTo(200);
        assertThat(limited).isEqualTo("😀".repeat(199) + "…");
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "status", "trackingNumber", "freeShippingThreshold", "a1"})
    void givenAFieldKey_whenChecked_thenItIsAccepted(String key) {
        assertThat(ActivityLimits.field(key)).isEqualTo(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Price", "tracking_number", "precio anterior", "1price", "ñandú"})
    void givenSomethingThatIsNotAFieldKey_whenChecked_thenItIsRefused(String key) {
        assertThatThrownBy(() -> ActivityLimits.field(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAFieldKeyOf41Characters_whenChecked_thenItIsRefused() {
        assertThatThrownBy(() -> ActivityLimits.field("a".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenUpTo20ChangedFields_whenChecked_thenTheyAreAccepted_andMoreAreRefused() {
        assertThat(ActivityLimits.changes(0)).isZero();
        assertThat(ActivityLimits.changes(20)).isEqualTo(20);
        assertThatThrownBy(() -> ActivityLimits.changes(21)).isInstanceOf(IllegalArgumentException.class);
    }
}
