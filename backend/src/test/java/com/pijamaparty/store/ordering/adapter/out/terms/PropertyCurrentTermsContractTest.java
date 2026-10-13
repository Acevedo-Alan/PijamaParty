package com.pijamaparty.store.ordering.adapter.out.terms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.application.CurrentTermsContractTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PropertyCurrentTermsContractTest extends CurrentTermsContractTest<PropertyCurrentTerms> {

    @Override
    protected PropertyCurrentTerms createSubject() {
        return new PropertyCurrentTerms(VERSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", " 2026-10", "2026-10 "})
    void givenABlankOrUntrimmedVersion_whenStarting_thenItFails(String version) {
        assertThatThrownBy(() -> new PropertyCurrentTerms(version)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenAVersionLongerThan40Characters_whenStarting_thenItFails() {
        new PropertyCurrentTerms("v".repeat(40));

        assertThatThrownBy(() -> new PropertyCurrentTerms("v".repeat(41))).isInstanceOf(IllegalStateException.class);
    }
}
