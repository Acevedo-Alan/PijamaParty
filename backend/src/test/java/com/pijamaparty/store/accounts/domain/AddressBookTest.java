package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-20: the rules of the address book. */
class AddressBookTest {

    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    static AddressDetails details(String street) {
        return new AddressDetails(street, "1234", Optional.empty(), Optional.empty(), "Santa Fe", Province.SANTA_FE,
            "S3000ABC");
    }

    private static AddressId id() {
        return AddressId.of(UUID.randomUUID());
    }

    private static Address added(AddressBook book, String street, boolean primary, Instant at) {
        return ((AddressBook.AddOutcome.Added) book.add(id(), details(street), primary, at)).address();
    }

    private static List<String> streets(AddressBook book) {
        return book.addresses().stream().map(address -> address.details().street()).toList();
    }

    private static String primaryStreet(AddressBook book) {
        return book.addresses().stream().filter(Address::primary).map(address -> address.details().street())
            .findFirst().orElseThrow();
    }

    @Test
    void givenAnEmptyBook_whenTheFirstAddressIsAdded_thenItIsThePrimaryWhateverWasAsked() {
        AddressBook book = AddressBook.of(ANA, List.of());

        Address first = added(book, "San Martín", false, T0);

        assertThat(first.primary()).isTrue();
        assertThat(first.createdAt()).isEqualTo(T0);
        assertThat(book.addresses()).containsExactly(first);
    }

    @Test
    void givenABookWithAPrimary_whenAnotherAddressIsAddedWithoutAskingForIt_thenThePrimaryStays() {
        AddressBook book = AddressBook.of(ANA, List.of());
        added(book, "San Martín", false, T0);

        Address second = added(book, "Rivadavia", false, T0.plusSeconds(1));

        assertThat(second.primary()).isFalse();
        assertThat(primaryStreet(book)).isEqualTo("San Martín");
    }

    @Test
    void givenAnAddressMarkedAsPrimary_whenAnotherOneIsMarked_thenThePreviousOneStopsBeingPrimary() {
        AddressBook book = AddressBook.of(ANA, List.of());
        Address first = added(book, "San Martín", false, T0);
        Address second = added(book, "Rivadavia", false, T0.plusSeconds(1));
        Instant marked = T0.plusSeconds(2);

        book.edit(second.id(), second.details(), true, marked);

        assertThat(primaryStreet(book)).isEqualTo("Rivadavia");
        assertThat(book.address(first.id())).get()
            .satisfies(previous -> {
                assertThat(previous.primary()).isFalse();
                assertThat(previous.updatedAt()).isEqualTo(marked);
            });
        assertThat(book.addresses().stream().filter(Address::primary)).hasSize(1);
    }

    @Test
    void givenABookWithAPrimary_whenANewAddressIsAddedAsPrimary_thenItReplacesThePreviousOne() {
        AddressBook book = AddressBook.of(ANA, List.of());
        added(book, "San Martín", false, T0);

        added(book, "Rivadavia", true, T0.plusSeconds(1));

        assertThat(primaryStreet(book)).isEqualTo("Rivadavia");
        assertThat(book.addresses().stream().filter(Address::primary)).hasSize(1);
    }

    @Test
    void givenACustomerWith10Addresses_whenItAddsAnother_thenItIsRefused() {
        AddressBook book = AddressBook.of(ANA, List.of());
        for (int i = 0; i < AddressBook.MAX_ADDRESSES; i++) {
            added(book, "Calle " + i, false, T0.plusSeconds(i));
        }

        assertThat(book.add(id(), details("Calle 11"), true, T0.plusSeconds(60)))
            .isEqualTo(new AddressBook.AddOutcome.Full());
        assertThat(book.addresses()).hasSize(AddressBook.MAX_ADDRESSES);
        assertThat(primaryStreet(book)).isEqualTo("Calle 0");
    }

    @Test
    void givenThePrimary_whenEditedWithoutMarkingIt_thenItStaysPrimaryWithItsNewFields() {
        AddressBook book = AddressBook.of(ANA, List.of());
        Address first = added(book, "San Martín", false, T0);
        added(book, "Rivadavia", false, T0.plusSeconds(1));

        Optional<Address> edited = book.edit(first.id(), details("Belgrano"), false, T0.plusSeconds(2));

        assertThat(edited).get().satisfies(address -> {
            assertThat(address.primary()).isTrue();
            assertThat(address.details().street()).isEqualTo("Belgrano");
            assertThat(address.createdAt()).isEqualTo(T0);
            assertThat(address.updatedAt()).isEqualTo(T0.plusSeconds(2));
        });
    }

    @Test
    void givenAnAddressEditedWithTheSameFields_whenEdited_thenItIsUnchanged() {
        AddressBook book = AddressBook.of(ANA, List.of());
        Address first = added(book, "San Martín", false, T0);

        assertThat(book.edit(first.id(), first.details(), true, T0.plusSeconds(5))).contains(first);
    }

    @Test
    void givenAnUnknownAddress_whenEditedOrDeleted_thenNothingChanges() {
        AddressBook book = AddressBook.of(ANA, List.of());
        Address first = added(book, "San Martín", false, T0);

        assertThat(book.edit(id(), details("Belgrano"), true, T0)).isEmpty();
        assertThat(book.remove(id(), T0)).isFalse();
        assertThat(book.addresses()).containsExactly(first);
    }

    @Test
    void givenThePrimary_whenDeleted_thenTheOldestRemainingAddressBecomesPrimary() {
        AddressBook book = AddressBook.of(ANA, List.of());
        added(book, "San Martín", false, T0);
        added(book, "Rivadavia", false, T0.plusSeconds(1));
        Address newest = added(book, "Belgrano", true, T0.plusSeconds(2));

        assertThat(book.remove(newest.id(), T0.plusSeconds(3))).isTrue();

        assertThat(primaryStreet(book)).isEqualTo("San Martín");
        assertThat(streets(book)).containsExactly("San Martín", "Rivadavia");
    }

    @Test
    void givenTheOnlyAddress_whenDeleted_thenTheBookIsEmpty() {
        AddressBook book = AddressBook.of(ANA, List.of());
        Address only = added(book, "San Martín", false, T0);

        book.remove(only.id(), T0.plusSeconds(1));

        assertThat(book.addresses()).isEmpty();
    }

    @Test
    void givenSeveralAddresses_whenListed_thenThePrimaryComesFirstAndTheRestFromTheOldest() {
        AddressBook book = AddressBook.of(ANA, List.of());
        added(book, "San Martín", false, T0);
        added(book, "Rivadavia", false, T0.plusSeconds(1));
        added(book, "Belgrano", true, T0.plusSeconds(2));
        added(book, "Urquiza", false, T0.plusSeconds(3));

        assertThat(streets(book)).containsExactly("Belgrano", "San Martín", "Rivadavia", "Urquiza");
    }

    @Test
    void givenStoredAddressesThatBreakTheRules_whenRebuilt_thenTheBookIsRefused() {
        Address one = new Address(id(), details("San Martín"), false, T0, T0);
        Address two = new Address(id(), details("Rivadavia"), true, T0, T0);
        Address three = new Address(id(), details("Belgrano"), true, T0, T0);

        assertThatThrownBy(() -> AddressBook.of(ANA, List.of(one))).hasMessageContaining("exactly one primary");
        assertThatThrownBy(() -> AddressBook.of(ANA, List.of(two, three))).hasMessageContaining("exactly one primary");
        assertThatThrownBy(() -> AddressBook.of(ANA, List.of(two, two))).hasMessageContaining("its own id");
    }

    @Test
    void givenTheProvincesOfTheShippingModule_thenTheyAreTheSameAsTheAddressBookOnes() {
        assertThat(Arrays.stream(com.pijamaparty.store.shipping.api.Province.values()).map(Enum::name))
            .containsExactlyElementsOf(Arrays.stream(Province.values()).map(Enum::name).toList());
    }

    @Test
    void givenAnAddress_whenPrinted_thenNoPersonalDataShows() {
        Address address = new Address(id(), details("San Martín"), true, T0, T0);

        assertThat(address.toString()).doesNotContain("San Martín", "S3000ABC", "Santa Fe");
        assertThat(address.details().toString()).doesNotContain("San Martín");
    }
}
