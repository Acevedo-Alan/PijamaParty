package com.pijamaparty.store.platform.application;

/**
 * Outbound port for new access tokens (RNF-29.6), so tests can control them. A token is a secret: it travels only to
 * its holder, and tables keep only its {@code AccessToken.hash()}.
 */
@FunctionalInterface
public interface TokenGenerator {

    /** Characters of every token. */
    int LENGTH = 43;

    /** 256 random bits in unpadded base64url: {@value #LENGTH} characters among letters, digits, '-' and '_'. */
    String newToken();
}
