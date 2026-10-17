package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.ListAddresses.Result;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListAddressesServiceTest {

    private final AddressBookFixture fixture = new AddressBookFixture();

    @Test
    void givenACustomerWithAddresses_whenListed_thenThePrimaryComesFirstAndThenTheOldest() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);
        AddressView second = fixture.added(fixture.ana, "Rivadavia", false);
        AddressView third = fixture.added(fixture.ana, "Belgrano", true);
        fixture.added(fixture.bruno, "Urquiza", false);

        assertThat(fixture.list.execute(fixture.ana)).isInstanceOfSatisfying(Result.Listed.class,
            listed -> assertThat(listed.addresses()).extracting(AddressView::id)
                .containsExactly(third.id(), first.id(), second.id()));
    }

    @Test
    void givenACustomerWithoutAddresses_whenListed_thenTheListIsEmpty() {
        assertThat(fixture.list.execute(fixture.ana)).isEqualTo(new Result.Listed(List.of()));
    }

    @Test
    void givenAGuestOrTheAdministrator_whenListing_thenItIsNotSignedIn() {
        assertThat(fixture.list.execute(Actor.anonymous(Optional.empty(), Optional.empty())))
            .isEqualTo(new Result.NotSignedIn());
        assertThat(fixture.list.execute(Actor.admin(UserId.of(UUID.randomUUID())))).isEqualTo(new Result.NotSignedIn());
    }
}
