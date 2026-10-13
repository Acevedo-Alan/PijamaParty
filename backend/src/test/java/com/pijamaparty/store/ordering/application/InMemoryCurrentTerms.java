package com.pijamaparty.store.ordering.application;

/** Fake of {@link CurrentTerms}: a fixed version. */
public record InMemoryCurrentTerms(String version) implements CurrentTerms {}
