package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import java.util.List;

/** Outbound port: new random recovery codes, all different (T-E1-16). */
public interface RecoveryCodeGenerator {

    int COUNT = 10;

    List<SecondFactorCode.Recovery> newCodes();
}
