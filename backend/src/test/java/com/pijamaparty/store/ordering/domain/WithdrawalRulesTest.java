package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-29 (T-E3-25): the order number as typed, the orders that may be withdrawn and the procedure code. */
class WithdrawalRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"1001", "#1001", "  #1001 ", "0001001"})
    void givenAnOrderNumberAsTheStoreShowsIt_whenParsed_thenItIsTheNumber(String typed) {
        assertThat(OrderNumberText.parse(typed)).isEqualTo(OptionalLong.of(1001));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"#", "0", "##1001", "1001#", "10 01", "-1001", "1e3", "abc", "1234567890123456789"})
    void givenTextThatIsNoOrderNumber_whenParsed_thenItIsNone(String typed) {
        assertThat(OrderNumberText.parse(typed)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED"})
    void givenAPaidOrder_whenAskedWhetherItMayBeWithdrawn_thenItMay(OrderStatus status) {
        assertThat(WithdrawalEligibility.allows(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "EXPIRED", "CANCELLED"})
    void givenAnOrderWithoutAPaymentToReturn_whenAskedWhetherItMayBeWithdrawn_thenItMayNot(OrderStatus status) {
        assertThat(WithdrawalEligibility.allows(status)).isFalse();
    }

    @Test
    void givenACodeOfThePrefixAnd8ReadableCharacters_whenCreated_thenItKeepsItsValue() {
        assertThat(new WithdrawalCode("ARR-7K2M9QXZ").value()).isEqualTo("ARR-7K2M9QXZ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARR-7K2M9QX", "ARR-7K2M9QXZA", "arr-7K2M9QXZ", "ARR-7K2M9QX0", "ARR-7K2M9QXO",
        "ARR-7K2M9QX1", "ARR-7K2M9QXI", "ARR-7K2M9QXL", "ARR-7k2m9qxz", "XYZ-7K2M9QXZ"})
    void givenAnythingElse_whenCreated_thenItIsRefused(String value) {
        assertThatThrownBy(() -> new WithdrawalCode(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTheAlphabet_whenRead_thenItLeavesOutTheCharactersReadAlike() {
        assertThat(WithdrawalCode.ALPHABET).hasSize(31).doesNotContain("0", "1", "I", "L", "O");
    }
}
