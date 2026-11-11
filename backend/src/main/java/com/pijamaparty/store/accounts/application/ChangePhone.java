package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-40 (T-E4-18): the signed-in customer adds or changes its phone from "Datos y acceso" ({@code PUT
 * /api/account/phone}), with the order's phone rule (UC-13). The next checkout prefills it (UC-21).
 */
public interface ChangePhone {

    Result execute(Command command);

    /** @param phone as typed ("342 400-0000"): personal data, never logged */
    record Command(Actor actor, String phone) {

        public Command {
            Objects.requireNonNull(actor, "actor");
        }

        @Override
        public String toString() {
            return "Command[actor=" + actor + ", ***]";
        }
    }

    sealed interface Result {

        /** @param phone the stored digits: personal data, never logged */
        record Changed(String phone) implements Result {

            @Override
            public String toString() {
                return "Changed[***]";
            }
        }

        /** Not 8 to 15 digits once spaces, {@code +}, {@code -} and parentheses are dropped: nothing changes. */
        record InvalidPhone() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
