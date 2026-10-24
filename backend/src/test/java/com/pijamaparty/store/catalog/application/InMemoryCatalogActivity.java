package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.testing.InMemoryActivityEntries;

/** Fake of {@link CatalogActivity}: keeps the entries in the order they were recorded. */
public class InMemoryCatalogActivity extends InMemoryActivityEntries implements CatalogActivity {
}
