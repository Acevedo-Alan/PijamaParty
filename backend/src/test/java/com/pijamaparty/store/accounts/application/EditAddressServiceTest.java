package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.EditAddress.Command;
import com.pijamaparty.store.accounts.application.EditAddress.Result;
import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditAddressServiceTest {

    private final AddressBookFixture fixture = new AddressBookFixture();

    @Test
    void givenAnAddressMarkedAsPrimary_whenAnotherOneIsMarked_thenThePreviousOneStopsBeingPrimary() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);
        AddressView second = fixture.added(fixture.ana, "Rivadavia", false);

        Result result = fixture.edit.execute(new Command(fixture.ana, second.id(),
            AddressBookFixture.form("Rivadavia"), true));

        assertThat(result).isInstanceOfSatisfying(Result.Edited.class,
            edited -> assertThat(edited.address().primary()).isTrue());
        assertThat(fixture.addressesOf(fixture.ana)).extracting(AddressView::id).containsExactly(second.id(),
            first.id());
        assertThat(fixture.addressesOf(fixture.ana)).filteredOn(AddressView::primary).extracting(AddressView::id)
            .containsExactly(second.id());
    }

    @Test
    void givenAnAddress_whenEdited_thenEveryFieldIsReplacedAndItKeepsItsPrimary() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);
        var form = new AddressForm("Bv. Pellegrini", "2500", "1", "A", "Rosario", Province.SANTA_FE, "2000");

        Result result = fixture.edit.execute(new Command(fixture.ana, first.id(), form, false));

        var expected = new AddressView(first.id(), "Bv. Pellegrini", "2500", Optional.of("1"), Optional.of("A"),
            "Rosario", Province.SANTA_FE, "2000", true);
        assertThat(result).isEqualTo(new Result.Edited(expected));
        assertThat(fixture.addressesOf(fixture.ana)).containsExactly(expected);
    }

    @Test
    void givenAnUnknownAddressOrAnotherCustomers_whenEdited_thenItIsNotFound() {
        AddressView brunos = fixture.added(fixture.bruno, "Rivadavia", false);
        fixture.added(fixture.ana, "San Martín", false);

        assertThat(fixture.edit.execute(new Command(fixture.ana, brunos.id(), AddressBookFixture.form("X"), true)))
            .isEqualTo(new Result.NotFound());
        assertThat(fixture.edit.execute(new Command(fixture.ana, AddressId.of(UUID.randomUUID()),
            AddressBookFixture.form("X"), true))).isEqualTo(new Result.NotFound());
        assertThat(fixture.addressesOf(fixture.bruno)).containsExactly(brunos);
    }

    @Test
    void givenFieldsThatBreakTheRules_whenEdited_thenTheyAreNamedAndTheAddressStaysAsItWas() {
        AddressView first = fixture.added(fixture.ana, "San Martín", false);
        var form = new AddressForm("San Martín", "", null, null, " ", Province.SANTA_FE, "S3000ABC");

        assertThat(fixture.edit.execute(new Command(fixture.ana, first.id(), form, false)))
            .isEqualTo(new Result.InvalidAddress(Set.of(AddressForm.Field.NUMBER, AddressForm.Field.CITY)));
        assertThat(fixture.addressesOf(fixture.ana)).containsExactly(first);
    }

    @Test
    void givenAGuest_whenEditingAnAddress_thenItIsNotSignedIn() {
        assertThat(fixture.edit.execute(new Command(Actor.anonymous(Optional.empty(), Optional.empty()),
            AddressId.of(UUID.randomUUID()), AddressBookFixture.form("X"), false))).isEqualTo(new Result.NotSignedIn());
    }
}
