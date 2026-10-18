package com.pijamaparty.store.shipping.application;

/**
 * RNF-31.6 (T-E5-12): the shipping costs and the free-shipping threshold that the storefront shows before a purchase.
 * Public and read only, as the catalog: the same for every visitor.
 */
public interface GetShippingRates {

    ShippingConfiguration execute();
}
