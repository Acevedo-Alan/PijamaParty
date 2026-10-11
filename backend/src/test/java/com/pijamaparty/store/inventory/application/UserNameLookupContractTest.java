package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link UserNameLookup}. */
public abstract class UserNameLookupContractTest<T extends UserNameLookup> extends ContractTest<T> {

    /** A user named {@code name} that the caller of {@link UserNameLookup#namesOf} can see. */
    protected abstract UserId givenVisibleUser(String name);

    @Test
    void givenAVisibleUserAndAnUnknownOne_whenLookingUpTheirNames_thenOnlyTheVisibleOneIsNamed() {
        UserId owner = givenVisibleUser("Dueña");
        UserId unknown = UserId.of(UUID.randomUUID());

        assertThat(subject.namesOf(Set.of(owner, unknown))).isEqualTo(Map.of(owner, "Dueña"));
    }

    @Test
    void givenNoUsers_whenLookingUpNames_thenThereAreNone() {
        assertThat(subject.namesOf(Set.of())).isEmpty();
    }
}
