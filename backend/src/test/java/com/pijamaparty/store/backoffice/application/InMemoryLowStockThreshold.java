package com.pijamaparty.store.backoffice.application;

/** Fake of {@link LowStockThreshold}: the units the test sets. */
public record InMemoryLowStockThreshold(int units) implements LowStockThreshold {}
