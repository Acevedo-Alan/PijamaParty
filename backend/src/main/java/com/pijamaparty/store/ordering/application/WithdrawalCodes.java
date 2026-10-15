package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.WithdrawalCode;

/** Outbound port (UC-29, T-E3-25): new random procedure codes, so tests can control them. */
@FunctionalInterface
public interface WithdrawalCodes {

    WithdrawalCode next();
}
