package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.accounts.application.DeleteAddress.Command;
import com.pijamaparty.store.accounts.application.DeleteAddress.Result;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteAddressServiceTest {

    private final AddressBookFixture fixture = new AddressBookFixture();

    @Test
    void givenThePrimary_whenDeleted_thenTheOldestRemainingAddressBecomesPrimary() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);
        AddressView second = fixture.added(fixture.ana, "Rivadavia", false);
        AddressView third = fixture.added(fixture.ana, "Belgrano", true);

        assertThat(fixture.delete.execute(new Command(fixture.ana, third.id()))).isEqualTo(new Result.Deleted());

        assertThat(fixture.addressesOf(fixture.ana)).extracting(AddressView::id, AddressView::primary)
            .containsExactly(tuple(first.id(), true),
                tuple(second.id(), false));
    }

    @Test
    void givenAnAddressThatIsNotThePrimary_whenDeleted_thenOnlyItGoes() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);
        AddressView second = fixture.added(fixture.ana, "Rivadavia", false);

        fixture.delete.execute(new Command(fixture.ana, second.id()));

        assertThat(fixture.addressesOf(fixture.ana)).containsExactly(first);
    }

    @Test
    void givenAnUnknownAddressOrAnotherCustomers_whenDeleted_thenItIsNotFoundAndNothingGoes() {
        AddressView brunos = fixture.added(fixture.bruno, "Rivadavia", false);

        assertThat(fixture.delete.execute(new Command(fixture.ana, brunos.id()))).isEqualTo(new Result.NotFound());
        assertThat(fixture.delete.execute(new Command(fixture.ana, AddressId.of(UUID.randomUUID()))))
            .isEqualTo(new Result.NotFound());
        assertThat(fixture.addressesOf(fixture.bruno)).containsExactly(brunos);
    }

    @Test
    void givenAGuest_whenDeletingAnAddress_thenItIsNotSignedIn() {
        assertThat(fixture.delete.execute(new Command(Actor.anonymous(Optional.empty(), Optional.empty()),
            AddressId.of(UUID.randomUUID())))).isEqualTo(new Result.NotSignedIn());
    }
}
