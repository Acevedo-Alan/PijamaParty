package com.pijamaparty.store.catalog.api;

/** UC-33: how many products are published, for the summary's totals. Read only. */
public interface PublishedProducts {

    long count();
}
