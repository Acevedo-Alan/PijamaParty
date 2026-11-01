package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import java.net.URI;
import java.util.Objects;

/**
 * UC-14: the order's payment at its provider. Called outside any transaction, as the order's buyer: it waits for the
 * provider, and runs its own short transactions.
 */
public interface OpenPayments {

    Opening open(OrderToPay order);

    sealed interface Opening {

        /** The provider's checkout to redirect the buyer to (MODO). */
        record Redirect(URI paymentUrl) implements Opening {

            public Redirect {
                Objects.requireNonNull(paymentUrl, "paymentUrl");
            }
        }

        /** T-E3-29: the buyer pays on the store's page, with the card form (MercadoPago). */
        record OnSite() implements Opening {}

        /** The provider could not open it; a retry may. */
        record Unavailable() implements Opening {}
    }
}
