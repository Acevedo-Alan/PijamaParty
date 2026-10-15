package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.ResolveWithdrawal.Command;
import com.pijamaparty.store.ordering.application.ResolveWithdrawal.Result;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResolveWithdrawalServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-08T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryWithdrawalStore store = new InMemoryWithdrawalStore();
    private final ResolveWithdrawalService service = new ResolveWithdrawalService(store,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED", "CANCELLED"})
    void givenAReceivedRequest_whenTheAdministratorResolvesIt_thenItIsResolvedNowWhateverItsOrdersStatus(
        OrderStatus status) {
        var order = store.place(status, REQUESTED_AT.minusSeconds(86_400), Optional.empty());
        UUID id = requested(order.id());

        assertThat(service.execute(new Command(ADMIN, id))).isEqualTo(new Result.Resolved(NOW));
        assertThat(store.codeOf(order.id())).isEmpty();
        assertThat(store.requests()).singleElement().extracting(InMemoryWithdrawalStore.Stored::resolvedAt)
            .isEqualTo(Optional.of(NOW));
    }

    @Test
    void givenAResolvedRequest_whenResolvedAgain_thenItKeepsItsResolutionDate() {
        UUID id = requested(store.place(OrderStatus.PAID, REQUESTED_AT, Optional.empty()).id());
        store.resolve(id, REQUESTED_AT.plusSeconds(60));

        assertThat(service.execute(new Command(ADMIN, id)))
            .isEqualTo(new Result.AlreadyResolved(REQUESTED_AT.plusSeconds(60)));
        assertThat(store.requests()).singleElement().extracting(InMemoryWithdrawalStore.Stored::resolvedAt)
            .isEqualTo(Optional.of(REQUESTED_AT.plusSeconds(60)));
    }

    @Test
    void givenAClockBehindTheRequest_whenResolved_thenItIsResolvedWhenItWasRequested() {
        var behind = new ResolveWithdrawalService(store, Clock.fixed(REQUESTED_AT.minusSeconds(5), ZoneOffset.UTC));
        UUID id = requested(store.place(OrderStatus.PAID, REQUESTED_AT.minusSeconds(60), Optional.empty()).id());

        assertThat(behind.execute(new Command(ADMIN, id))).isEqualTo(new Result.Resolved(REQUESTED_AT));
    }

    @Test
    void givenNoSuchRequest_whenResolved_thenItIsNotFound() {
        assertThat(service.execute(new Command(ADMIN, UUID.randomUUID()))).isEqualTo(new Result.NotFound());
    }

    @Test
    void givenSomeoneButTheAdministrator_whenResolving_thenItIsForbiddenAndNothingChanges() {
        UUID id = requested(store.place(OrderStatus.PAID, REQUESTED_AT, Optional.empty()).id());

        assertThat(service.execute(new Command(Actor.system(), id))).isEqualTo(new Result.Forbidden());
        assertThat(store.requests()).singleElement().extracting(InMemoryWithdrawalStore.Stored::resolvedAt)
            .isEqualTo(Optional.empty());
    }

    private UUID requested(OrderId order) {
        UUID id = UUID.randomUUID();
        store.insert(new NewWithdrawal(id, order, new WithdrawalCode("ARR-7K2M9QXZ"), false, REQUESTED_AT));
        return id;
    }
}
