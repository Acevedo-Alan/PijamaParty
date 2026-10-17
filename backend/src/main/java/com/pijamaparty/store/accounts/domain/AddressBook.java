package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A customer's address book (UC-20): at most {@value #MAX_ADDRESSES} addresses and, when it has any, exactly one
 * primary. The first address becomes the primary; marking another one unmarks the previous one, and deleting the
 * primary makes the oldest remaining address primary, so the checkout always has one to preselect (UC-21).
 */
public final class AddressBook {

    public static final int MAX_ADDRESSES = 10;

    // The id breaks ties between addresses added at the same instant, as the store reads them.
    private static final Comparator<Address> OLDEST_FIRST =
        Comparator.comparing(Address::createdAt).thenComparing(address -> address.id().value());

    private final UserId owner;
    private final List<Address> addresses;

    private AddressBook(UserId owner, List<Address> addresses) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.addresses = new ArrayList<>(addresses);
        this.addresses.sort(OLDEST_FIRST);
        if (addresses.size() > MAX_ADDRESSES) {
            throw new IllegalArgumentException("at most " + MAX_ADDRESSES + " addresses");
        }
        if (new HashSet<>(addresses.stream().map(Address::id).toList()).size() != addresses.size()) {
            throw new IllegalArgumentException("each address has its own id");
        }
        long primaries = addresses.stream().filter(Address::primary).count();
        if (addresses.isEmpty() ? primaries != 0 : primaries != 1) {
            throw new IllegalArgumentException("a book with addresses has exactly one primary");
        }
    }

    /** Rebuilds a stored address book, or the empty one of a customer without addresses. */
    public static AddressBook of(UserId owner, List<Address> addresses) {
        return new AddressBook(owner, addresses);
    }

    public UserId owner() {
        return owner;
    }

    /** The primary first, then from the oldest to the newest. */
    public List<Address> addresses() {
        List<Address> listed = new ArrayList<>(addresses);
        listed.sort(Comparator.comparing((Address address) -> !address.primary()).thenComparing(OLDEST_FIRST));
        return List.copyOf(listed);
    }

    public Optional<Address> address(AddressId id) {
        return addresses.stream().filter(address -> address.id().equals(id)).findFirst();
    }

    /** Adds an address; the first one is the primary whatever {@code primary} says. */
    public AddOutcome add(AddressId id, AddressDetails details, boolean primary, Instant at) {
        if (addresses.size() >= MAX_ADDRESSES) {
            return new AddOutcome.Full();
        }
        if (address(id).isPresent()) {
            throw new IllegalArgumentException("each address has its own id");
        }
        boolean makePrimary = primary || addresses.isEmpty();
        if (makePrimary) {
            unmarkPrimary(at);
        }
        Address added = new Address(id, details, makePrimary, at, at);
        addresses.add(added);
        return new AddOutcome.Added(added);
    }

    /**
     * Replaces the fields of an address. {@code markPrimary} makes it the primary; false leaves it as it was, because
     * the primary is only ever replaced by another one.
     */
    public Optional<Address> edit(AddressId id, AddressDetails details, boolean markPrimary, Instant at) {
        Optional<Address> current = address(id);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        if (markPrimary && !current.get().primary()) {
            unmarkPrimary(at);
        }
        Address edited = current.get().with(details, markPrimary || current.get().primary(), at);
        addresses.set(addresses.indexOf(current.get()), edited);
        return Optional.of(edited);
    }

    /** Deletes an address; when it was the primary, the oldest remaining one becomes primary. */
    public boolean remove(AddressId id, Instant at) {
        Optional<Address> removed = address(id);
        if (removed.isEmpty()) {
            return false;
        }
        addresses.remove(removed.get());
        if (removed.get().primary() && !addresses.isEmpty()) {
            Address oldest = addresses.stream().min(OLDEST_FIRST).orElseThrow();
            addresses.set(addresses.indexOf(oldest), oldest.with(oldest.details(), true, at));
        }
        return true;
    }

    private void unmarkPrimary(Instant at) {
        addresses.replaceAll(address -> address.primary() ? address.with(address.details(), false, at) : address);
    }

    public sealed interface AddOutcome {

        record Added(Address address) implements AddOutcome {}

        /** The book already has {@value AddressBook#MAX_ADDRESSES} addresses. */
        record Full() implements AddOutcome {}
    }
}
