package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.settings.domain.StoreSettings;
import java.util.Objects;
import java.util.Set;

/** UC-36: the administrator saves "Datos de la tienda", every field at once, from the version it read. */
public interface UpdateStoreSettings {

    Result execute(Command command);

    /** @param version the version the form was opened with */
    record Command(Actor actor, long version, StoreSettings.Typed values) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(values, "values");
        }
    }

    sealed interface Result {

        record Updated(StoreSettings settings) implements Result {}

        /** Nothing is saved; these fields break their rules. */
        record Invalid(Set<StoreSettings.Field> fields) implements Result {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }

        /** Another save got in first; nothing is saved, and these are the settings now. */
        record VersionChanged(StoreSettings current) implements Result {}

        record Forbidden() implements Result {}
    }
}
