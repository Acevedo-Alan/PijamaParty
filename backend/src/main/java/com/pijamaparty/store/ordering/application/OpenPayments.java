package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import java.net.URI;
import java.util.Optional;

/**
 * UC-14: the order's payment at its provider. Called outside any transaction, as the order's buyer: it waits for the
 * provider, and runs its own short transactions.
 */
public interface OpenPayments {

    /** The provider's checkout to redirect the buyer to; empty when the provider could not open it. */
    Optional<URI> open(OrderToPay order);
}
