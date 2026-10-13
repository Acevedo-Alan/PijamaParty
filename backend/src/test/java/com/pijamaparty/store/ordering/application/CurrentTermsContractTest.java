package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link CurrentTerms} (RNF-30.6): what every implementation gives, configured with "2026-10". */
public abstract class CurrentTermsContractTest<T extends CurrentTerms> extends ContractTest<T> {

    protected static final String VERSION = "2026-10";

    @Test
    void givenTheTermsInForce_whenAskedForTheirVersion_thenItIsTheConfiguredOneWithinTheColumnLimits() {
        String version = subject.version();

        assertThat(version).isEqualTo(VERSION).isEqualTo(version.strip());
        assertThat(version.length()).isBetween(1, 40);
    }
}
