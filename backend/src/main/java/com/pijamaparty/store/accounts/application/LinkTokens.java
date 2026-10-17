package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.application.TokenGenerator;
import java.util.regex.Pattern;

/** The shape of the token of an email link (UC-19): one that could never have been issued is not looked up. */
final class LinkTokens {

    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{" + TokenGenerator.LENGTH + "}");

    private LinkTokens() {
    }

    static boolean isWellFormed(String token) {
        return token != null && WELL_FORMED.matcher(token).matches();
    }
}
