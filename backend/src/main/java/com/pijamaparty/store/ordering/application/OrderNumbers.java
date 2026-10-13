package com.pijamaparty.store.ordering.application;

/** Outbound port: the numbers of new orders (RNF-20.6), unique and increasing from 1001, shown as #1001. */
@FunctionalInterface
public interface OrderNumbers {

    /** Never the same twice, even when the transaction that took it rolls back (gaps are fine). */
    long next();
}
