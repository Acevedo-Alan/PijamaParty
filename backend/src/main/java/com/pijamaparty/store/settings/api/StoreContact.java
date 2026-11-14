package com.pijamaparty.store.settings.api;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-36, T-E3-33: the ways to reach the store, for the footer of its emails. Read only; joins the caller's
 * transaction.
 */
public interface StoreContact {

    Contact contact();

    /**
     * Each value empty when the store has none.
     *
     * @param whatsAppNumber digits only, in the international format {@code wa.me} takes, such as {@code 5493424000000}
     */
    record Contact(Optional<String> email, Optional<String> whatsAppNumber, Optional<String> instagramUrl,
                   Optional<String> facebookUrl, Optional<String> tiktokUrl) {

        public static final Contact NONE =
            new Contact(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        public Contact {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(whatsAppNumber, "whatsAppNumber");
            Objects.requireNonNull(instagramUrl, "instagramUrl");
            Objects.requireNonNull(facebookUrl, "facebookUrl");
            Objects.requireNonNull(tiktokUrl, "tiktokUrl");
        }
    }
}
