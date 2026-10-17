package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.accounts.application.AddAddress.Command;
import com.pijamaparty.store.accounts.application.AddAddress.Result;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddAddressServiceTest {

    private final AddressBookFixture fixture = new AddressBookFixture();

    @Test
    void givenACustomerWithoutAddresses_whenItAddsOne_thenItIsStoredNormalizedAsThePrimary() {
        Result result = fixture.add.execute(new Command(fixture.ana,
            new AddressForm(" San Martín ", "1234", " 3 ", "", "Santa Fe", Province.SANTA_FE, "s3000abc"), false));

        var expected = new AddressView(AddressId.of(InMemoryIdGenerator.nth(1)), "San Martín", "1234",
            Optional.of("3"), Optional.empty(), "Santa Fe", Province.SANTA_FE, "S3000ABC", true);
        assertThat(result).isEqualTo(new Result.Added(expected));
        assertThat(fixture.addressesOf(fixture.ana)).containsExactly(expected);
    }

    @Test
    void givenAnAddressMarkedAsPrimary_whenANewOneIsAddedAsPrimary_thenThePreviousOneStopsBeingPrimary() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);

        AddressView second = fixture.added(fixture.ana, "Rivadavia", true);

        assertThat(second.primary()).isTrue();
        assertThat(fixture.addressesOf(fixture.ana)).extracting(AddressView::id, AddressView::primary)
            .containsExactly(tuple(second.id(), true),
                tuple(first.id(), false));
    }

    @Test
    void givenACustomerWith10Addresses_whenItAddsAnother_thenItIsRefused() {
        for (int i = 0; i < AddressBook.MAX_ADDRESSES; i++) {
            fixture.added(fixture.ana, "Calle " + i, false);
        }

        assertThat(fixture.add.execute(new Command(fixture.ana, AddressBookFixture.form("Calle 11"), true)))
            .isEqualTo(new Result.AddressBookFull());
        assertThat(fixture.addressesOf(fixture.ana)).hasSize(AddressBook.MAX_ADDRESSES);
    }

    @Test
    void givenFieldsThatBreakTheRules_whenAdded_thenTheyAreNamedAndNothingIsStored() {
        var form = new AddressForm("", "1234", null, null, "Santa Fe", Province.SANTA_FE, "3000AB");

        assertThat(fixture.add.execute(new Command(fixture.ana, form, false)))
            .isEqualTo(new Result.InvalidAddress(Set.of(AddressForm.Field.STREET, AddressForm.Field.POSTAL_CODE)));
        assertThat(fixture.addressesOf(fixture.ana)).isEmpty();
    }

    @Test
    void givenAGuestTheAdministratorOrADeletedAccount_whenAddingAnAddress_thenItIsNotSignedIn() {
        for (Actor actor : new Actor[] {
            Actor.anonymous(Optional.empty(), Optional.empty()),
            Actor.admin(UserId.of(UUID.randomUUID())),
            Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty())}) {
            assertThat(fixture.add.execute(new Command(actor, AddressBookFixture.form("San Martín"), false)))
                .isEqualTo(new Result.NotSignedIn());
        }
    }
}
