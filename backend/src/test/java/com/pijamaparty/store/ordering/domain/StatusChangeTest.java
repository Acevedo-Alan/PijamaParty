package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.StatusChange.Decision;
import com.pijamaparty.store.ordering.domain.StatusChange.StockEffect;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatusChangeTest {

    private static final Optional<ShipmentTrackingNumber> NUMBER =
        Optional.of(new ShipmentTrackingNumber("CA123456789AR"));

    @Test
    void givenEachStatus_whenAskingWhatTheAdministratorMayMoveItTo_thenOnlyTheDiagramsManualStepsAreOffered() {
        assertThat(StatusChange.nextFor(OrderStatus.AWAITING_PAYMENT)).containsExactly(OrderStatus.CANCELLED);
        assertThat(StatusChange.nextFor(OrderStatus.PAID))
            .containsExactly(OrderStatus.PREPARING, OrderStatus.CANCELLED);
        assertThat(StatusChange.nextFor(OrderStatus.PREPARING))
            .containsExactly(OrderStatus.SHIPPED, OrderStatus.CANCELLED);
        assertThat(StatusChange.nextFor(OrderStatus.SHIPPED)).containsExactly(OrderStatus.DELIVERED);
        assertThat(StatusChange.nextFor(OrderStatus.DELIVERED)).isEmpty();
        assertThat(StatusChange.nextFor(OrderStatus.CANCELLED)).isEmpty();
        assertThat(StatusChange.nextFor(OrderStatus.EXPIRED)).isEmpty();
    }

    @Test
    void givenAnOrderAwaitingPayment_whenMovedToShipped_thenItIsRejected() {
        assertThat(StatusChange.decide(OrderStatus.AWAITING_PAYMENT, OrderStatus.SHIPPED, NUMBER, Optional.empty()))
            .isEqualTo(new Decision.InvalidTransition());
    }

    @Test
    void givenStatusesOnlyThePaymentOrTheExpirySet_whenTheAdministratorAsksForThem_thenTheyAreRejected() {
        assertThat(StatusChange.decide(OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, Optional.empty(),
            Optional.empty())).isEqualTo(new Decision.InvalidTransition());
        assertThat(StatusChange.decide(OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, Optional.empty(),
            Optional.empty())).isEqualTo(new Decision.InvalidTransition());
    }

    @Test
    void givenAPreparingOrder_whenShipped_thenItTakesTheTrackingNumberAndNeedsIt() {
        assertThat(StatusChange.decide(OrderStatus.PREPARING, OrderStatus.SHIPPED, NUMBER, Optional.empty()))
            .isEqualTo(new Decision.Allowed(new StatusChange(OrderStatus.PREPARING, OrderStatus.SHIPPED, NUMBER,
                StockEffect.NONE)));
        assertThat(StatusChange.decide(OrderStatus.PREPARING, OrderStatus.SHIPPED, Optional.empty(),
            Optional.empty())).isEqualTo(new Decision.MissingTrackingNumber());
    }

    @Test
    void givenAnyOtherChange_whenItCarriesATrackingNumber_thenItIsRefused() {
        assertThat(StatusChange.decide(OrderStatus.PAID, OrderStatus.PREPARING, NUMBER, Optional.empty()))
            .isEqualTo(new Decision.UnexpectedField("shipmentTrackingNumber"));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenCancelled_thenItsReservationIsReleasedWithoutAsking() {
        assertThat(StatusChange.decide(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, Optional.empty(),
            Optional.empty())).isEqualTo(new Decision.Allowed(new StatusChange(OrderStatus.AWAITING_PAYMENT,
            OrderStatus.CANCELLED, Optional.empty(), StockEffect.RELEASE)));
        assertThat(StatusChange.decide(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, Optional.empty(),
            Optional.of(true))).isEqualTo(new Decision.UnexpectedField("restock"));
    }

    @Test
    void givenAPaidOrPreparingOrder_whenCancelled_thenTheAdministratorDecidesWhetherTheStockGoesBack() {
        for (OrderStatus from : new OrderStatus[] {OrderStatus.PAID, OrderStatus.PREPARING}) {
            assertThat(StatusChange.decide(from, OrderStatus.CANCELLED, Optional.empty(), Optional.of(true)))
                .isEqualTo(new Decision.Allowed(new StatusChange(from, OrderStatus.CANCELLED, Optional.empty(),
                    StockEffect.RESTOCK)));
            assertThat(StatusChange.decide(from, OrderStatus.CANCELLED, Optional.empty(), Optional.of(false)))
                .isEqualTo(new Decision.Allowed(new StatusChange(from, OrderStatus.CANCELLED, Optional.empty(),
                    StockEffect.NONE)));
            assertThat(StatusChange.decide(from, OrderStatus.CANCELLED, Optional.empty(), Optional.empty()))
                .isEqualTo(new Decision.RestockDecisionRequired());
        }
    }

    @Test
    void givenEachStatus_whenAskingWhetherTheTrackingNumberCanBeCorrected_thenOnlyAShippedOrderAllowsIt() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(StatusChange.allowsTrackingNumberCorrection(status)).as(status.name())
                .isEqualTo(status == OrderStatus.SHIPPED);
        }
    }

    @Test
    void givenAChangeThatIsNotACancellation_whenItCarriesARestockAnswer_thenItIsRefused() {
        assertThat(StatusChange.decide(OrderStatus.SHIPPED, OrderStatus.DELIVERED, Optional.empty(),
            Optional.of(false))).isEqualTo(new Decision.UnexpectedField("restock"));
    }
}
