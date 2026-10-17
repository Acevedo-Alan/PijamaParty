package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-28 (T-E4-16): the signed-in customer downloads its data ({@code GET /api/account/data}). */
public interface ExportCustomerData {

    Result execute(Actor actor);

    sealed interface Result {

        record Exported(CustomerDataExport data) implements Result {

            public Exported {
                Objects.requireNonNull(data, "data");
            }
        }

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
