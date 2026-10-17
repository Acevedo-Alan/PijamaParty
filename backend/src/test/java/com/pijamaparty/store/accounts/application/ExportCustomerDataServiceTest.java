package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.api.CustomerDataContributor;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-28 (T-E4-16): the signed-in customer downloads its own data, with the sections other modules hold. */
class ExportCustomerDataServiceTest {

    private static final Instant LATER = Instant.parse("2026-10-17T18:00:00Z");
    private static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");

    private final AddressBookFixture fixture = new AddressBookFixture();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemorySignInMethods methods = new InMemorySignInMethods(fixture.users, identities);
    private final Clock clock = Clock.fixed(LATER, ZoneOffset.UTC);

    /** The orders of {@code ordering}. */
    private final InMemoryCustomerDataContributor orders =
        new InMemoryCustomerDataContributor(CustomerDataSection.ORDERS);

    @Test
    void givenACustomerWithAnAddressALinkAndOrders_whenExporting_thenItGetsOnlyItsOwnDataWithEverySection() {
        fixture.added(fixture.ana, "San Martín", true);
        fixture.added(fixture.bruno, "Rivadavia", true);
        UserId ana = fixture.ana.userId().orElseThrow();
        orders.arrange(ana, List.of("order of ana"));
        orders.arrange(fixture.bruno.userId().orElseThrow(), List.of("order of bruno"));
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana, GOOGLE,
            fixture.users.findById(ana).orElseThrow().email(), AddressBookFixture.T0));

        var result = service(List.of(orders)).execute(fixture.ana);

        assertThat(result).isInstanceOfSatisfying(ExportCustomerData.Result.Exported.class, exported -> {
            CustomerDataExport data = exported.data();
            assertThat(data.exportedAt()).isEqualTo(LATER);
            assertThat(data.profile().email()).isEqualTo("ana@example.com");
            assertThat(data.profile().createdAt()).isEqualTo(AddressBookFixture.T0);
            assertThat(data.signInMethods().hasPassword()).isTrue();
            assertThat(data.signInMethods().identities()).extracting(SignInMethodsView.Identity::provider)
                .containsExactly(ExternalProvider.GOOGLE);
            assertThat(data.addresses()).extracting(AddressView::street).containsExactly("San Martín");
            assertThat(List.<Object>copyOf(data.section(CustomerDataSection.ORDERS))).containsExactly("order of ana");
            assertThat(data.section(CustomerDataSection.REVIEWS)).isEmpty();
        });
    }

    @Test
    void givenNoContributors_whenExporting_thenEverySectionIsAnEmptyList() {
        var result = service(List.of()).execute(fixture.ana);

        assertThat(result).isInstanceOfSatisfying(ExportCustomerData.Result.Exported.class, exported -> {
            for (CustomerDataSection section : CustomerDataSection.values()) {
                assertThat(exported.data().section(section)).as(section.name()).isEmpty();
            }
        });
    }

    @Test
    void givenAGuest_whenExporting_thenItIsNotSignedIn() {
        assertThat(service(List.of(orders)).execute(Actor.anonymous(Optional.empty(), Optional.empty())))
            .isEqualTo(new ExportCustomerData.Result.NotSignedIn());
    }

    @Test
    void givenACustomerWhoseAccountNoLongerExists_whenExporting_thenItIsNotSignedIn() {
        Actor deleted = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(service(List.of(orders)).execute(deleted)).isEqualTo(new ExportCustomerData.Result.NotSignedIn());
    }

    @Test
    void givenTwoContributorsOfOneSection_whenCreatingTheService_thenItIsRefused() {
        assertThatThrownBy(() -> service(List.of(orders, orders))).isInstanceOf(IllegalArgumentException.class);
    }

    private ExportCustomerDataService service(List<CustomerDataContributor> contributors) {
        return new ExportCustomerDataService(fixture.users, methods, fixture.books, contributors, clock);
    }
}
