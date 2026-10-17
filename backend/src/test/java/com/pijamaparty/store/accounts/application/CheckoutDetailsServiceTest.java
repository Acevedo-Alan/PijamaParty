package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.accounts.api.CheckoutDetails;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-21 (T-E4-06): a customer's purchase leaves its phone, and its address if asked, for the next checkout. */
class CheckoutDetailsServiceTest {

    private static final CheckoutDetails.Address ADDRESS = new CheckoutDetails.Address("Rivadavia", "550",
        Optional.of("2"), Optional.of("B"), "Rosario", "SANTA_FE", "S2000ABC");

    private final AddressBookFixture fixture = new AddressBookFixture();
    private final CheckoutDetails checkoutDetails = new CheckoutDetailsService(fixture.books, fixture.books,
        fixture.users, fixture.users, fixture.ids, fixture.clock);
    private final UserId ana = fixture.ana.userId().orElseThrow();

    @Test
    void givenACustomerWithoutAPhone_whenItBuys_thenTheOrdersPhoneBecomesTheAccounts() {
        checkoutDetails.remember(ana, "3425551234", Optional.empty());

        User user = fixture.users.findById(ana).orElseThrow();
        assertThat(user.phone()).contains("3425551234");
        assertThat(user.updatedAt()).isEqualTo(AddressBookFixture.T0);
        assertThat(fixture.addressesOf(fixture.ana)).isEmpty();
    }

    @Test
    void givenACustomerWithAPhone_whenItBuysWithAnotherOne_thenTheNewOneReplacesIt() {
        checkoutDetails.remember(ana, "3425551234", Optional.empty());

        checkoutDetails.remember(ana, "3415559876", Optional.empty());

        assertThat(fixture.users.findById(ana).orElseThrow().phone()).contains("3415559876");
    }

    @Test
    void givenACustomerWithoutAddresses_whenItBuysAndSavesTheAddress_thenItIsItsPrimary() {
        checkoutDetails.remember(ana, "3425551234", Optional.of(ADDRESS));

        assertThat(fixture.addressesOf(fixture.ana)).singleElement().satisfies(address -> {
            assertThat(address.street()).isEqualTo("Rivadavia");
            assertThat(address.number()).isEqualTo("550");
            assertThat(address.floor()).contains("2");
            assertThat(address.apartment()).contains("B");
            assertThat(address.city()).isEqualTo("Rosario");
            assertThat(address.province()).isEqualTo(Province.SANTA_FE);
            assertThat(address.postalCode()).isEqualTo("S2000ABC");
            assertThat(address.primary()).isTrue();
        });
    }

    @Test
    void givenACustomerWithAPrimaryAddress_whenItBuysAndSavesAnother_thenTheNewOneIsNotThePrimary() {
        AddressView primary = fixture.added(fixture.ana, "San Martín", true);

        checkoutDetails.remember(ana, "3425551234", Optional.of(ADDRESS));

        assertThat(fixture.addressesOf(fixture.ana)).extracting(AddressView::street, AddressView::primary)
            .containsExactly(tuple(primary.street(), true), tuple("Rivadavia", false));
    }

    @Test
    void givenAFullAddressBook_whenItBuysAndSavesTheAddress_thenTheBookIsUnchangedAndThePhoneIsStillSaved() {
        for (int i = 0; i < AddressBook.MAX_ADDRESSES; i++) {
            fixture.added(fixture.ana, "Calle " + i, false);
        }

        checkoutDetails.remember(ana, "3425551234", Optional.of(ADDRESS));

        assertThat(fixture.addressesOf(fixture.ana)).hasSize(AddressBook.MAX_ADDRESSES)
            .extracting(AddressView::street).doesNotContain("Rivadavia");
        assertThat(fixture.users.findById(ana).orElseThrow().phone()).contains("3425551234");
    }

    @Test
    void givenAnAccountThatNoLongerExists_whenItsOrderIsPlaced_thenNothingIsRemembered() {
        UserId deleted = UserId.of(UUID.randomUUID());

        checkoutDetails.remember(deleted, "3425551234", Optional.of(ADDRESS));

        assertThat(fixture.users.findById(deleted)).isEmpty();
        assertThat(fixture.books.findByCustomer(deleted).addresses()).isEmpty();
    }
}
