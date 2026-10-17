package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Address;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressDetails;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadAddressBook} and {@link SaveAddressBook} over the same storage (UC-20). The owners are the
 * customers of {@link RlsPolicyTester}, so a real adapter can act as each of them (Row Level Security, RNF-27).
 */
public abstract class AddressBookStoreContractTest<T extends LoadAddressBook & SaveAddressBook>
    extends ContractTest<T> {

    public static final UserId ANA = RlsPolicyTester.CUSTOMER_A_ID;
    public static final UserId BRUNO = RlsPolicyTester.CUSTOMER_B_ID;

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    /** Creates the account of a customer, which the book belongs to. */
    protected abstract void arrangeAccount(UserId customer);

    @BeforeEach
    void theAccountsExist() {
        arrangeAccount(ANA);
        arrangeAccount(BRUNO);
    }

    private static AddressDetails details(String street, Optional<String> floor) {
        return new AddressDetails(street, "1234", floor, Optional.of("B"), "Santa Fe", Province.SANTA_FE, "S3000ABC");
    }

    private static Address add(AddressBook book, String street, boolean primary, Instant at) {
        var outcome = book.add(AddressId.of(UUID.randomUUID()), details(street, Optional.empty()), primary, at);
        return ((AddressBook.AddOutcome.Added) outcome).address();
    }

    @Test
    void givenACustomerWithoutAddresses_whenItsBookIsRead_thenItIsEmpty() {
        assertThat(subject.findByCustomer(ANA).addresses()).isEmpty();
        assertThat(subject.lockByCustomer(ANA)).get().satisfies(book -> assertThat(book.addresses()).isEmpty());
    }

    @Test
    void givenASavedBook_whenRead_thenItHasTheSameAddressesInTheSameOrder() {
        AddressBook book = subject.lockByCustomer(ANA).orElseThrow();
        book.add(AddressId.of(UUID.randomUUID()), details("San Martín", Optional.of("3")), false, T0);
        add(book, "Rivadavia", false, T0.plusSeconds(1));
        add(book, "Belgrano", true, T0.plusSeconds(2));

        subject.save(book);
        persistAndForget();

        assertThat(subject.findByCustomer(ANA).addresses()).containsExactlyElementsOf(book.addresses());
        assertThat(subject.lockByCustomer(ANA).orElseThrow().addresses()).containsExactlyElementsOf(book.addresses());
    }

    @Test
    void givenAnAddressDeletedAndThePrimaryMoved_whenSaved_thenTheStoredBookIsExactlyTheNewOne() {
        AddressBook book = subject.lockByCustomer(ANA).orElseThrow();
        Address first = add(book, "San Martín", false, T0);
        Address second = add(book, "Rivadavia", false, T0.plusSeconds(1));
        Address third = add(book, "Belgrano", false, T0.plusSeconds(2));
        subject.save(book);
        persistAndForget();

        AddressBook changed = subject.lockByCustomer(ANA).orElseThrow();
        changed.edit(third.id(), third.details(), true, T0.plusSeconds(3));
        changed.remove(second.id(), T0.plusSeconds(4));
        subject.save(changed);
        persistAndForget();

        AddressBook stored = subject.findByCustomer(ANA);
        assertThat(stored.addresses()).containsExactlyElementsOf(changed.addresses());
        assertThat(stored.addresses()).extracting(Address::id).containsExactly(third.id(), first.id());
        assertThat(stored.address(first.id())).get().extracting(Address::primary).isEqualTo(false);
    }

    @Test
    void givenTwoCustomersWithAddresses_whenEachBookIsRead_thenEachHasOnlyItsOwn() {
        AddressBook ana = subject.lockByCustomer(ANA).orElseThrow();
        add(ana, "San Martín", false, T0);
        subject.save(ana);
        AddressBook bruno = subject.lockByCustomer(BRUNO).orElseThrow();
        add(bruno, "Rivadavia", false, T0);
        subject.save(bruno);
        persistAndForget();

        assertThat(subject.findByCustomer(ANA).addresses()).containsExactlyElementsOf(ana.addresses());
        assertThat(subject.findByCustomer(BRUNO).addresses()).containsExactlyElementsOf(bruno.addresses());
    }

    @Test
    void givenAnAccountThatDoesNotExist_whenItsBookIsLocked_thenThereIsNone() {
        assertThat(subject.lockByCustomer(UserId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenACustomerWith10Addresses_whenSaved_thenAllOfThemAreKept() {
        AddressBook book = subject.lockByCustomer(ANA).orElseThrow();
        for (int i = 0; i < AddressBook.MAX_ADDRESSES; i++) {
            add(book, "Calle " + i, false, T0.plusSeconds(i));
        }

        subject.save(book);
        persistAndForget();

        assertThat(subject.findByCustomer(ANA).addresses()).hasSize(AddressBook.MAX_ADDRESSES);
        assertThat(List.copyOf(subject.findByCustomer(ANA).addresses())).containsExactlyElementsOf(book.addresses());
    }
}
