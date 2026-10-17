package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pijamaparty.store.ordering.application.RememberCheckoutDetails.AddressToSave;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link RememberCheckoutDetails} (UC-21, T-E4-06). */
public abstract class RememberCheckoutDetailsContractTest<T extends RememberCheckoutDetails>
    extends ContractTest<T> {

    protected static final AddressToSave ADDRESS = new AddressToSave(new ShippingAddress("Rivadavia", "550",
        Optional.of("2"), Optional.empty(), "Rosario", "S2000ABC"), Province.SANTA_FE);

    /** A customer account without a phone or addresses. */
    protected abstract UserId givenCustomer();

    /** Fills the customer's address book up to its limit of 10, none of them on Rivadavia. */
    protected abstract void givenFullAddressBook(UserId customer);

    protected abstract Optional<String> phoneOf(UserId customer);

    /** The streets of the customer's address book. */
    protected abstract List<String> streetsOf(UserId customer);

    @Test
    void givenACustomer_whenItsCheckoutIsRemembered_thenTheAccountHasTheOrdersPhoneAndNoNewAddress() {
        UserId customer = givenCustomer();

        subject.remember(customer, "3425551234", Optional.empty());

        assertThat(phoneOf(customer)).contains("3425551234");
        assertThat(streetsOf(customer)).isEmpty();
    }

    @Test
    void givenACustomerThatSavesTheAddress_whenItsCheckoutIsRemembered_thenTheAddressJoinsItsBook() {
        UserId customer = givenCustomer();

        subject.remember(customer, "3425551234", Optional.of(ADDRESS));

        assertThat(streetsOf(customer)).containsExactly("Rivadavia");
    }

    @Test
    void givenAFullAddressBook_whenTheAddressIsSaved_thenItIsLeftOutAndThePhoneIsStillRemembered() {
        UserId customer = givenCustomer();
        givenFullAddressBook(customer);

        subject.remember(customer, "3425551234", Optional.of(ADDRESS));

        assertThat(streetsOf(customer)).hasSize(10).doesNotContain("Rivadavia");
        assertThat(phoneOf(customer)).contains("3425551234");
    }

    @Test
    void givenAnAccountThatNoLongerExists_whenItsCheckoutIsRemembered_thenNothingFails() {
        UserId deleted = UserId.of(UUID.randomUUID());

        assertThatCode(() -> subject.remember(deleted, "3425551234", Optional.of(ADDRESS)))
            .doesNotThrowAnyException();
        assertThat(streetsOf(deleted)).isEmpty();
    }
}
