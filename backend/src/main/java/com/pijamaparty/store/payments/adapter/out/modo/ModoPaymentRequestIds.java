package com.pijamaparty.store.payments.adapter.out.modo;

import java.util.regex.Pattern;

/**
 * UC-15 (T-E3b-03): what a MODO payment request id looks like. MODO's ids are UUIDs; anything else is never asked to
 * MODO, so neither a notification nor the reconciliation can put an arbitrary path into a request.
 */
public final class ModoPaymentRequestIds {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]{1,100}");

    private ModoPaymentRequestIds() {
    }

    public static boolean isValid(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
