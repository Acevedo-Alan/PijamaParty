package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.MovableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What the address book use-case tests share (UC-20): two customers, their fakes and a clock that moves. */
final class AddressBookFixture {

    static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    final InMemoryUserStore users = new InMemoryUserStore();
    final InMemoryAddressBookStore books = new InMemoryAddressBookStore(users);
    final InMemoryIdGenerator ids = new InMemoryIdGenerator();
    final MovableClock clock = new MovableClock(T0);
    final Actor ana = customer("ana@example.com");
    final Actor bruno = customer("bruno@example.com");

    final AddAddress add = new AddAddressService(books, books, ids, clock);
    final EditAddress edit = new EditAddressService(books, books, clock);
    final DeleteAddress delete = new DeleteAddressService(books, books, clock);
    final ListAddresses list = new ListAddressesService(books);

    static AddressForm form(String street) {
        return new AddressForm(street, "1234", null, null, "Santa Fe", Province.SANTA_FE, "s3000abc");
    }

    private Actor customer(String email) {
        User user = User.newCustomer(UserId.of(UUID.randomUUID()), new Email(email), "Ana",
            UserStoreContractTest.HASH, T0);
        users.save(user);
        return Actor.customer(user.id(), Optional.empty(), Optional.empty());
    }

    /** Adds an address a second after the previous one, so the order of the book is the order of the calls. */
    AddressView added(Actor actor, String street, boolean primary) {
        clock.advance(Duration.ofSeconds(1));
        return ((AddAddress.Result.Added) add.execute(new AddAddress.Command(actor, form(street), primary)))
            .address();
    }

    List<AddressView> addressesOf(Actor actor) {
        return ((ListAddresses.Result.Listed) list.execute(actor)).addresses();
    }
}
