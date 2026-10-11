package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link VariantAvailabilityLookup}: the stock inventory says can be sold. */
public abstract class VariantAvailabilityLookupContractTest<T extends VariantAvailabilityLookup>
    extends ContractTest<T> {

    /** Gives the variant a stock level with {@code units} available, as opening and adjusting it would. */
    protected abstract void givenAvailable(VariantId variant, int units);

    @Test
    void givenVariantsWithAndWithoutStock_whenLookedUp_thenEveryOneIsAnsweredAndThoseWithoutHave0() {
        VariantId stocked = VariantId.of(UUID.randomUUID());
        VariantId soldOut = VariantId.of(UUID.randomUUID());
        VariantId unknown = VariantId.of(UUID.randomUUID());
        givenAvailable(stocked, 5);
        givenAvailable(soldOut, 0);

        assertThat(subject.availableOf(List.of(stocked, soldOut, unknown)))
            .isEqualTo(Map.of(stocked, 5, soldOut, 0, unknown, 0));
    }

    @Test
    void givenNoVariants_whenLookedUp_thenTheAnswerIsEmpty() {
        assertThat(subject.availableOf(List.of())).isEmpty();
    }
}
