package com.pijamaparty.store.ordering.adapter.out.terms;

import com.pijamaparty.store.ordering.application.CurrentTerms;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RNF-30.6: the version of the terms in force, from {@code store.ordering.terms-version}. The business publishes the
 * legal texts; whoever changes them changes this version, and every order keeps the one its buyer accepted.
 */
@Component
class PropertyCurrentTerms implements CurrentTerms {

    static final int MAX_LENGTH = 40;

    private final String version;

    PropertyCurrentTerms(@Value("${store.ordering.terms-version}") String version) {
        if (version == null || version.isBlank() || !version.equals(version.strip())
            || version.length() > MAX_LENGTH) {
            throw new IllegalStateException(
                "store.ordering.terms-version must be 1 to " + MAX_LENGTH + " characters without surrounding spaces");
        }
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
