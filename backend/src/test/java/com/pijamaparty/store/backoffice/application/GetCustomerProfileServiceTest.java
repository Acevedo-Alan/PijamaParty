package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderRow;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-34 (T-E8-11): a customer's profile, with its orders, its purchase figures and the record of the view. */
class GetCustomerProfileServiceTest {

    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);
    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    private final InMemoryCustomerAccounts accounts = new InMemoryCustomerAccounts();
    private final InMemoryCustomerOrderHistories orders = new InMemoryCustomerOrderHistories();
    private final InMemoryBackofficeActivity activity = new InMemoryBackofficeActivity();
    private final GetCustomerProfile profile = new GetCustomerProfileService(accounts, orders, activity);

    private UUID customer() {
        UUID id = UUID.randomUUID();
        accounts.add(new CustomerProfile(id, "Ana Gómez", "ana@example.com", true, Optional.of("3425551234"), T0,
            List.of(), List.of()));
        return id;
    }

    @Test
    void givenACustomerWithThreeOrders_whenOpened_thenAllShowOnePurchaseCountsAndTheViewIsRecordedWithItsIdOnly() {
        UUID ana = customer();
        orders.order(Optional.of(ana), Status.DELIVERED, 3_000_000, T0);
        orders.order(Optional.of(ana), Status.CANCELLED, 1_000_000, T0.plusSeconds(60));
        orders.order(Optional.of(ana), Status.AWAITING_PAYMENT, 500_000, T0.plusSeconds(120));
        orders.order(Optional.empty(), Status.PAID, 700_000, T0.plusSeconds(180));

        var shown = (GetCustomerProfile.Result.Shown) profile.execute(new GetCustomerProfile.Query(ADMIN, ana, 0));

        assertThat(shown.profile().name()).isEqualTo("Ana Gómez");
        assertThat(shown.orders().orders()).extracting(OrderRow::status)
            .containsExactly(Status.AWAITING_PAYMENT, Status.CANCELLED, Status.DELIVERED);
        assertThat(shown.orders().orderCount()).isEqualTo(3);
        assertThat(shown.orders().purchases()).isEqualTo(1);
        assertThat(shown.orders().spent()).isEqualTo(Money.of(3_000_000));
        assertThat(shown.orders().lastPurchase()).contains(T0);
        assertThat(activity.entries()).containsExactly(new ActivityEntry(ADMIN_ID, ActivityAction.CUSTOMER_VIEWED,
            ActivitySubject.of(SubjectType.CUSTOMER, ana)));
        assertThat(activity.entries().toString()).doesNotContain("Ana", "ana@");
        assertThat(shown.toString()).doesNotContain("Ana", "ana@");
    }

    @Test
    void givenAnOpenedProfile_whenItsSecondPageOfOrdersLoads_thenNoSecondViewIsRecorded() {
        UUID ana = customer();
        IntStream.rangeClosed(1, 21).forEach(i -> orders.order(Optional.of(ana), Status.PAID, 100, T0.plusSeconds(i)));

        profile.execute(new GetCustomerProfile.Query(ADMIN, ana, 0));
        var second = (GetCustomerProfile.Result.Shown) profile.execute(new GetCustomerProfile.Query(ADMIN, ana, 1));

        assertThat(second.orders().orders()).hasSize(1);
        assertThat(activity.entries()).hasSize(1);
    }

    @Test
    void givenAnUnknownOrDeletedCustomer_whenOpened_thenItIsNotFoundAndNothingIsRecorded() {
        UUID ana = customer();
        accounts.remove(ana);

        assertThat(profile.execute(new GetCustomerProfile.Query(ADMIN, ana, 0)))
            .isEqualTo(new GetCustomerProfile.Result.NotFound());
        assertThat(profile.execute(new GetCustomerProfile.Query(ADMIN, UUID.randomUUID(), 0)))
            .isEqualTo(new GetCustomerProfile.Result.NotFound());
        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenOpening_thenItIsForbiddenAndNothingIsRecorded() {
        UUID ana = customer();
        Actor customer = Actor.customer(UserId.of(ana), Optional.empty(), Optional.empty());

        assertThat(profile.execute(new GetCustomerProfile.Query(customer, ana, 0)))
            .isEqualTo(new GetCustomerProfile.Result.Forbidden());
        assertThat(activity.entries()).isEmpty();
        assertThatThrownBy(() -> new GetCustomerProfile.Query(ADMIN, ana, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
