package com.pijamaparty.store.catalog.application;

/** Outbound read port (UC-33): how many products are published, within the caller's transaction. */
public interface CountPublishedProducts {

    long publishedProducts();
}
