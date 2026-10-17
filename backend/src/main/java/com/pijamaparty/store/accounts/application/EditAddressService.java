package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EditAddressService implements EditAddress {

    private final LoadAddressBook books;
    private final SaveAddressBook saveBook;
    private final Clock clock;

    EditAddressService(LoadAddressBook books, SaveAddressBook saveBook, Clock clock) {
        this.books = books;
        this.saveBook = saveBook;
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
                yield book.get().edit(command.id(), valid.address(), command.markPrimary(), clock.instant())
                    .<Result>map(edited -> {
                        saveBook.save(book.get());
                        return new Result.Edited(AddressView.of(edited));
                    })
                    .orElseGet(Result.NotFound::new);
            }
        };
    }
}
