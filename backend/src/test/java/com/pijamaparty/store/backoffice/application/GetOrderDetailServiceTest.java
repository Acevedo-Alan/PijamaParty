package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.application.GetOrderDetail.Query;
import com.pijamaparty.store.backoffice.application.GetOrderDetail.Result;
import com.pijamaparty.store.backoffice.domain.OrderAlert;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Buyer;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetOrderDetailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryOrderDetails orders = new InMemoryOrderDetails();
    private final InMemoryBuyerEmails emails = new InMemoryBuyerEmails();
    private final GetOrderDetailService service = new GetOrderDetailService(orders, emails);

    @Test
    void givenAnOrderWhoseBuyersEmailFailed_whenTheAdministratorOpensTheDetail_thenAnAlertShowsWithThePhone() {
        AdminOrder order = order(false);
        orders.add(order);
        emails.failed(order.orderId());

        assertThat(service.execute(new Query(ADMIN, order.orderId())))
            .isEqualTo(new Result.Found(order, List.of(OrderAlert.BUYER_EMAIL_FAILED)));
        assertThat(order.buyer().phone()).isEqualTo("3425551234");
    }

    @Test
    void givenAnOrderPaidAfterItExpired_whenTheAdministratorOpensTheDetail_thenItIsToBeRefunded() {
        AdminOrder order = order(true);
        orders.add(order);

        assertThat(service.execute(new Query(ADMIN, order.orderId())))
            .isEqualTo(new Result.Found(order, List.of(OrderAlert.PAID_AFTER_CLOSING)));
    }

    @Test
    void givenAnOrderWithoutProblems_whenOpened_thenItHasNoAlerts() {
        AdminOrder order = order(false);
        orders.add(order);

        assertThat(service.execute(new Query(ADMIN, order.orderId())))
            .isEqualTo(new Result.Found(order, List.of()));
    }

    @Test
    void givenNoSuchOrder_whenOpened_thenItIsNotFound() {
        assertThat(service.execute(new Query(ADMIN, UUID.randomUUID()))).isEqualTo(new Result.NotFound());
    }

    @Test
    void givenSomeoneButTheAdministrator_whenOpeningAnOrder_thenItIsForbidden() {
        AdminOrder order = order(false);
        orders.add(order);

        assertThat(service.execute(new Query(Actor.system(), order.orderId()))).isEqualTo(new Result.Forbidden());
    }

    private static AdminOrder order(boolean paidAfterClosing) {
        return new AdminOrder(UUID.randomUUID(), 1001, paidAfterClosing ? Status.EXPIRED : Status.PAID,
            paidAfterClosing ? List.of() : List.of(Status.PREPARING, Status.CANCELLED), NOW, NOW, Optional.empty(),
            new Buyer("Ana María Pérez", "ana@example.com", "3425551234", "12345678"),
            new Address("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "SANTA_FE", "S3000ABC"),
            List.of(new Line("Pijama Luna", "M", "Rosa", 1, Money.of(2_000_000), Money.of(2_000_000))),
            Money.of(2_000_000), Money.zero(), Optional.empty(), Money.of(300_000), Money.of(2_300_000),
            "MERCADOPAGO", Optional.empty(), paidAfterClosing);
    }
}
