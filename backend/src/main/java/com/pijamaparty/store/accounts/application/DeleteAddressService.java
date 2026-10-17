package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteAddressService implements DeleteAddress {

    private final LoadAddressBook books;
    private final SaveAddressBook saveBook;
    private final Clock clock;

    DeleteAddressService(LoadAddressBook books, SaveAddressBook saveBook, Clock clock) {
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
        Optional<AddressBook> book = books.lockByCustomer(command.actor().userId().get());
        if (book.isEmpty()) {
            return new Result.NotSignedIn();
        }
        if (!book.get().remove(command.id(), clock.instant())) {
            return new Result.NotFound();
        }
        saveBook.save(book.get());
        return new Result.Deleted();
    }
}
