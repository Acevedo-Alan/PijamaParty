package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddAddressService implements AddAddress {

    private final LoadAddressBook books;
    private final SaveAddressBook saveBook;
    private final IdGenerator ids;
    private final Clock clock;

    AddAddressService(LoadAddressBook books, SaveAddressBook saveBook, IdGenerator ids, Clock clock) {
        this.books = books;
        this.saveBook = saveBook;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        return switch (command.address().validate()) {
            case AddressForm.Outcome.Invalid invalid -> new Result.InvalidAddress(invalid.fields());
            case AddressForm.Outcome.Valid valid -> {
                Optional<AddressBook> book = books.lockByCustomer(command.actor().userId().get());
                if (book.isEmpty()) {
                    yield new Result.NotSignedIn();
                }
                yield switch (book.get().add(AddressId.of(ids.newId()), valid.address(), command.primary(),
                    clock.instant())) {
                    case AddressBook.AddOutcome.Full full -> new Result.AddressBookFull();
                    case AddressBook.AddOutcome.Added added -> {
                        saveBook.save(book.get());
                        yield new Result.Added(AddressView.of(added.address()));
                    }
                };
            }
        };
    }
}
