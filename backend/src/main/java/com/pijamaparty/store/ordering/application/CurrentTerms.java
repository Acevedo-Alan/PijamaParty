package com.pijamaparty.store.ordering.application;

/** Outbound port: the version of the terms and the exchange and returns policy in force (RNF-30.6). */
@FunctionalInterface
public interface CurrentTerms {

    /** 1 to 40 characters, trimmed. */
    String version();
}
