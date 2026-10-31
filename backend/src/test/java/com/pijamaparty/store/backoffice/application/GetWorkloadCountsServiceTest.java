package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.application.GetWorkloadCounts.Query;
import com.pijamaparty.store.backoffice.application.GetWorkloadCounts.Result;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetWorkloadCountsServiceTest {

    private final InMemoryOrderCounts orderCounts = new InMemoryOrderCounts();
    private final GetWorkloadCountsService service = new GetWorkloadCountsService(orderCounts);

    @Test
    void givenTwoPaidOrders_whenTheBackOfficeOpens_thenPedidosShowsTwo() {
        orderCounts.paid();
        orderCounts.paid();

        assertThat(service.execute(new Query(Actor.admin(UserId.of(UUID.randomUUID())))))
            .isEqualTo(new Result.Counted(2));
    }

    @Test
    void givenACustomer_whenAskingForTheCounts_thenItIsForbidden() {
        orderCounts.paid();

        Actor customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(service.execute(new Query(customer))).isEqualTo(new Result.Forbidden());
        assertThat(service.execute(new Query(Actor.anonymous(Optional.empty(), Optional.empty()))))
            .isEqualTo(new Result.Forbidden());
    }
}
