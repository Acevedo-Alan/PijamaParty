package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.domain.SetupItem;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadOmittedSetupItems} and {@link SaveOmittedSetupItems} together (UC-46): omitting and showing
 * again are idempotent, and what was omitted is read back.
 */
public abstract class OmittedSetupItemsContractTest<T extends LoadOmittedSetupItems & SaveOmittedSetupItems>
    extends ContractTest<T> {

    private static final Instant AT = Instant.parse("2026-11-24T15:00:00Z");

    @Test
    void givenNothingOmitted_whenRead_thenItIsEmpty() {
        assertThat(subject.omitted()).isEmpty();
    }

    @Test
    void givenTheBannerOmittedTwice_whenRead_thenItIsOmittedOnce() {
        subject.omit(SetupItem.HOME_BANNER, AT);
        subject.omit(SetupItem.HOME_BANNER, AT.plusSeconds(60));
        persistAndForget();

        assertThat(subject.omitted()).containsExactly(SetupItem.HOME_BANNER);
    }

    @Test
    void givenBothOmittableItemsOmittedAndOneShownAgain_whenRead_thenOnlyTheOtherIsOmitted() {
        subject.omit(SetupItem.INSTAGRAM, AT);
        subject.omit(SetupItem.HOME_BANNER, AT);
        subject.show(SetupItem.INSTAGRAM);
        persistAndForget();

        assertThat(subject.omitted()).containsExactly(SetupItem.HOME_BANNER);
    }

    @Test
    void givenAnItemNeverOmitted_whenShownAgain_thenNothingChanges() {
        subject.show(SetupItem.INSTAGRAM);
        persistAndForget();

        assertThat(subject.omitted()).isEmpty();
    }
}
