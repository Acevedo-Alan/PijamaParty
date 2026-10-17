package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDataSection;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-28 (T-E4-16): everything the store holds of a customer, for its data download: its profile, how it signs in, its
 * address book and the sections other modules hold, every one present. Personal data, the owner's own:
 * {@link #toString()} never prints it (R-05).
 */
public record CustomerDataExport(Instant exportedAt, Profile profile, SignInMethodsView signInMethods,
                                 List<AddressView> addresses, Map<CustomerDataSection, List<?>> sections) {

    public CustomerDataExport {
        Objects.requireNonNull(exportedAt, "exportedAt");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(signInMethods, "signInMethods");
        addresses = List.copyOf(addresses);
        var every = new EnumMap<CustomerDataSection, List<?>>(CustomerDataSection.class);
        for (CustomerDataSection section : CustomerDataSection.values()) {
            every.put(section, List.copyOf(sections.getOrDefault(section, List.of())));
        }
        sections = Map.copyOf(every);
    }

    public List<?> section(CustomerDataSection section) {
        return sections.get(section);
    }

    @Override
    public String toString() {
        return "CustomerDataExport[exportedAt=" + exportedAt + ", ***]";
    }

    /** @param phone left by its last purchase (T-E4-06); empty until then */
    public record Profile(String email, String name, Optional<String> phone, boolean emailVerified,
                          Instant createdAt) {

        public Profile {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(phone, "phone");
            Objects.requireNonNull(createdAt, "createdAt");
        }

        @Override
        public String toString() {
            return "Profile[***]";
        }
    }
}
