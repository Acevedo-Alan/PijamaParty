package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link LoadCustomerProfile}: the users, addresses and linked accounts the test gives, in the database's order. */
public class InMemoryLoadCustomerProfile implements LoadCustomerProfile {

    private record User(CustomerProfile profile, String role) {}

    private record Added<V>(UUID userId, V value, Instant at) {}

    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final List<Added<SavedAddress>> addresses = new CopyOnWriteArrayList<>();
    private final List<Added<SignInProvider>> providers = new CopyOnWriteArrayList<>();

    public void addUser(UUID id, String name, String email, boolean emailVerified, String phone, String role,
                        Instant registeredAt) {
        users.put(id, new User(new CustomerProfile(id, name, email, emailVerified, Optional.ofNullable(phone),
            registeredAt, List.of(), List.of()), role));
    }

    public void addAddress(UUID userId, SavedAddress address, Instant addedAt) {
        addresses.add(new Added<>(userId, address, addedAt));
    }

    public void link(UUID userId, SignInProvider provider, Instant linkedAt) {
        providers.add(new Added<>(userId, provider, linkedAt));
    }

    @Override
    public Optional<CustomerProfile> find(UUID customerId) {
        User user = users.get(customerId);
        if (user == null || !user.role().equals("CUSTOMER")) {
            return Optional.empty();
        }
        CustomerProfile stored = user.profile();
        List<SavedAddress> book = addresses.stream()
            .filter(added -> added.userId().equals(customerId))
            .sorted(Comparator.<Added<SavedAddress>, Boolean>comparing(added -> !added.value().primary())
                .thenComparing(Added::at)
                .thenComparing(added -> added.value().id().toString()))
            .map(Added::value)
            .toList();
        List<SignInProvider> linked = providers.stream()
            .filter(added -> added.userId().equals(customerId))
            .sorted(Comparator.<Added<SignInProvider>, Instant>comparing(Added::at)
                .thenComparing(added -> added.value().name()))
            .map(Added::value)
            .toList();
        return Optional.of(new CustomerProfile(stored.id(), stored.name(), stored.email(), stored.emailVerified(),
            stored.phone(), stored.registeredAt(), linked, book));
    }
}
