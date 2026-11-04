package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadCustomerProfile} (UC-34, T-E8-11): a user with role {@code CUSTOMER}, its addresses (the main
 * one first, then in the order they were added) and the providers of its linked accounts, in the order they were
 * linked; never an administrator.
 */
public abstract class LoadCustomerProfileContractTest<T extends LoadCustomerProfile> extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    protected static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    /** Stores a user as it is; {@code role} is {@code CUSTOMER} or {@code ADMIN}; {@code phone} may be null. */
    protected abstract void givenUser(UUID id, String name, String email, boolean emailVerified, String phone,
                                      String role, Instant registeredAt);

    /** Stores an address of {@code userId} in Santa Fe, added at {@code addedAt}. */
    protected abstract void givenAddress(UUID userId, SavedAddress address, Instant addedAt);

    /** Links an external account of {@code provider} to {@code userId} at {@code linkedAt}. */
    protected abstract void givenLinkedAccount(UUID userId, SignInProvider provider, Instant linkedAt);

    private static SavedAddress address(String street, boolean primary) {
        return new SavedAddress(UUID.randomUUID(), street, "1234", Optional.empty(), Optional.empty(), "Santa Fe",
            "SANTA_FE", "S3000ABC", primary);
    }

    @Test
    void givenACustomerWithAddressesAndLinkedAccounts_whenFound_thenAllShowInOrder() {
        UUID id = UUID.randomUUID();
        givenUser(id, "Ana Gómez", "ana@example.com", true, "3425551234", "CUSTOMER", T0);
        SavedAddress first = address("Rivadavia", false);
        SavedAddress main = new SavedAddress(UUID.randomUUID(), "San Martín", "2550", Optional.of("3"),
            Optional.of("B"), "Santa Fe", "SANTA_FE", "S3000ABC", true);
        SavedAddress last = address("Mitre", false);
        givenAddress(id, first, T0.plusSeconds(60));
        givenAddress(id, main, T0.plusSeconds(120));
        givenAddress(id, last, T0.plusSeconds(180));
        givenLinkedAccount(id, SignInProvider.FACEBOOK, T0.plusSeconds(60));
        givenLinkedAccount(id, SignInProvider.GOOGLE, T0.plusSeconds(120));
        persistAndForget();

        assertThat(subject.find(id)).contains(new CustomerProfile(id, "Ana Gómez", "ana@example.com", true,
            Optional.of("3425551234"), T0, List.of(SignInProvider.FACEBOOK, SignInProvider.GOOGLE),
            List.of(main, first, last)));
    }

    @Test
    void givenACustomerWithNothingSaved_whenFound_thenItHasNoPhoneAddressesOrProviders() {
        UUID id = UUID.randomUUID();
        givenUser(id, "Juan Pérez", "juan@example.com", false, null, "CUSTOMER", T0);
        persistAndForget();

        assertThat(subject.find(id)).contains(new CustomerProfile(id, "Juan Pérez", "juan@example.com", false,
            Optional.empty(), T0, List.of(), List.of()));
    }

    @Test
    void givenTheAdministratorOrAnUnknownId_whenFound_thenThereIsNone() {
        UUID admin = UUID.randomUUID();
        givenUser(admin, "Dueña", "owner@example.com", true, null, "ADMIN", T0);
        persistAndForget();

        assertThat(subject.find(admin)).isEmpty();
        assertThat(subject.find(UUID.randomUUID())).isEmpty();
    }
}
