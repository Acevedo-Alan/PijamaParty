package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CartId;

/** Outbound port: deletes one cart with its items, within the caller's transaction. */
@FunctionalInterface
public interface DeleteCart {

    /**
     * UC-19 (T-E4-04): the guest cart that joins the customer's. Answers whether it was there: a concurrent sign-in
     * that deleted it first merged it already.
     */
    boolean delete(CartId cartId);
}
