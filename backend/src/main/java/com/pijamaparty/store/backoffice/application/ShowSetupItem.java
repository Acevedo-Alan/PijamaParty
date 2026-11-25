package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupItem;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-46: "Volver a mostrar" on an omitted item of the store setup guide. Idempotent: any item may be shown. */
public interface ShowSetupItem {

    Result execute(Command command);

    record Command(Actor actor, SetupItem item) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(item, "item");
        }
    }

    sealed interface Result {

        record Shown() implements Result {}

        record Forbidden() implements Result {}
    }
}
