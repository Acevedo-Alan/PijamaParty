package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.TotpSecret;

/** Outbound port: a new random secret for the authenticator app, at least 160 bits (RFC 4226). */
public interface TotpSecretGenerator {

    TotpSecret newSecret();
}
