package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDirectory;
import com.pijamaparty.store.accounts.domain.CustomerSearch;
import com.pijamaparty.store.accounts.domain.RegistrationPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-34 (T-E8-10): {@link CustomerDirectory} for the back office, read as the caller (the administrator). */
@Service
class CustomerDirectoryService implements CustomerDirectory {

    private final CustomerPages pages;
    private final Clock clock;

    CustomerDirectoryService(CustomerPages pages, Clock clock) {
        this.pages = pages;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Listing list(Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        Optional<CustomerSearch> parsed;
        switch (CustomerSearch.parse(search.orElse(""))) {
            case CustomerSearch.Parsed.Invalid invalid -> {
                return new Listing.InvalidSearch();
            }
            case CustomerSearch.Parsed.None none -> parsed = Optional.empty();
            case CustomerSearch.Parsed.Valid valid -> parsed = Optional.of(valid.search());
        }
        Optional<RegistrationPeriod> period = RegistrationPeriod.of(from, to);
        if (period.isEmpty()) {
            return new Listing.InvalidPeriod();
        }
        // R-07: the dates are the store's, the zone of the injected clock.
        var filter = new CustomerFilter(parsed, period.get().start(clock.getZone()), period.get().end(clock.getZone()));
        // One more than a page tells whether another page follows.
        List<Customer> rows = pages.page(filter, Math.multiplyExact(page, PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Listing.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
