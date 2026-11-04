package com.pijamaparty.store.backoffice.application;

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
 * Contract of {@link CustomerAccounts} (UC-34, T-E8-11): a customer with its contact, linked providers and addresses;
 * never the administrator or an unknown id.
 */
public abstract class CustomerAccountsContractTest<T extends CustomerAccounts> extends ContractTest<T> {

    protected static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    /** A verified customer with the phone 3425551234, registered at {@link #T0}, Google linked and this address. */
    protected abstract UUID givenCustomer(String name, String email, SavedAddress address);

    protected abstract UUID givenAdministrator();

    @Test
    void givenACustomer_whenFound_thenItsContactProvidersAndAddressesShow() {
        SavedAddress address = new SavedAddress(UUID.randomUUID(), "San Martín", "2550", Optional.of("3"),
            Optional.empty(), "Santa Fe", "SANTA_FE", "S3000ABC", true);
        UUID ana = givenCustomer("Ana Gómez", "ana@example.com", address);
        persistAndForget();

        assertThat(subject.find(ana)).contains(new CustomerProfile(ana, "Ana Gómez", "ana@example.com", true,
            Optional.of("3425551234"), T0, List.of(SignInProvider.GOOGLE), List.of(address)));
    }

    @Test
    void givenTheAdministratorOrAnUnknownId_whenFound_thenThereIsNone() {
        UUID admin = givenAdministrator();
        persistAndForget();

        assertThat(subject.find(admin)).isEmpty();
        assertThat(subject.find(UUID.randomUUID())).isEmpty();
    }
}
