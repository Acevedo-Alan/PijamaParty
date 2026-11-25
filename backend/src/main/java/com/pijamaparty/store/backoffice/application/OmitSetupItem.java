package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupItem;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-46: "Omitir" on an item of the store setup guide the store may do without. Idempotent. */
public interface OmitSetupItem {

    Result execute(Command command);

    record Command(Actor actor, SetupItem item) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(item, "item");
        }
    }

    sealed interface Result {

        record Omitted() implements Result {}

        /** What a working store needs (WhatsApp, email, size guides, photos) stays until done. */
        record NotOmittable() implements Result {}

        record Forbidden() implements Result {}
    }
}
