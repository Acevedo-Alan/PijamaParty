package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupGuide;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-46: the store setup guide of the summary, "Tu tienda, lista". */
public interface GetSetupGuide {

    Result execute(Query query);

    record Query(Actor actor) {

        public Query {
            Objects.requireNonNull(actor, "actor");
        }
    }

    sealed interface Result {

        record Shown(SetupGuide guide) implements Result {}

        record Forbidden() implements Result {}
    }
}
