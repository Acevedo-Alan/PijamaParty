package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.settings.domain.StoreSettings;
import java.util.Objects;

/** UC-36: the administrator opens "Datos de la tienda" with every setting and its version. */
public interface GetStoreSettings {

    Result execute(Query query);

    record Query(Actor actor) {

        public Query {
            Objects.requireNonNull(actor, "actor");
        }
    }

    sealed interface Result {

        record Found(StoreSettings settings) implements Result {}

        record Forbidden() implements Result {}
    }
}
