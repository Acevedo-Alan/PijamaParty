package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CheckoutDetails;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-21 (T-E4-06): the checkout's phone and, if asked, its address, left on the customer's account. */
@Service
class CheckoutDetailsService implements CheckoutDetails {

    private final LoadAddressBook books;
    private final SaveAddressBook saveBook;
    private final LoadUser users;
    private final SaveUser saveUser;
    private final IdGenerator ids;
    private final Clock clock;

    CheckoutDetailsService(LoadAddressBook books, SaveAddressBook saveBook, LoadUser users, SaveUser saveUser,
                           IdGenerator ids, Clock clock) {
        this.books = books;
        this.saveBook = saveBook;
        this.users = users;
        this.saveUser = saveUser;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(UserId customer, String phone, Optional<Address> address) {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(address, "address");
        // The lock comes first, as in every change of the book, so a concurrent change waits for this one.
        Optional<AddressBook> book = books.lockByCustomer(customer);
        Optional<User> user = users.findById(customer);
        if (book.isEmpty() || user.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        if (!user.get().phone().equals(Optional.of(phone))) {
            user.get().rememberPhone(phone, now);
            saveUser.save(user.get());
        }
        address.ifPresent(each -> addToBook(book.get(), each, now));
    }

    private void addToBook(AddressBook book, Address address, Instant now) {
        var form = new AddressForm(address.street(), address.number(), address.floor().orElse(null),
            address.apartment().orElse(null), address.city(), Province.valueOf(address.province()),
            address.postalCode());
        // The order's address already passed the same rules (UC-13); one that does not is not worth failing the order.
        if (form.validate() instanceof AddressForm.Outcome.Valid valid
            && book.add(AddressId.of(ids.newId()), valid.address(), false, now)
                instanceof AddressBook.AddOutcome.Added) {
            saveBook.save(book);
        }
    }
}
