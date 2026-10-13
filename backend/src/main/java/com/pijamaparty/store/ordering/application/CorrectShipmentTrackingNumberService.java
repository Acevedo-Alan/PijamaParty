package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CorrectShipmentTrackingNumberService implements CorrectShipmentTrackingNumber {

    private final OrderStatuses orderStatuses;
    private final ShipmentTrackingNumbers trackingNumbers;
    private final Clock clock;

    CorrectShipmentTrackingNumberService(OrderStatuses orderStatuses, ShipmentTrackingNumbers trackingNumbers,
                                         Clock clock) {
        this.orderStatuses = orderStatuses;
        this.trackingNumbers = trackingNumbers;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<ShipmentTrackingNumber> number = ShipmentTrackingNumber.parse(command.shipmentTrackingNumber());
        if (number.isEmpty()) {
            return new Result.InvalidTrackingNumber();
        }
        Optional<OrderStatus> current = orderStatuses.lockStatus(command.order());
        if (current.isEmpty()) {
            return new Result.NotFound();
        }
        if (!StatusChange.allowsTrackingNumberCorrection(current.get())) {
            return new Result.NotShipped(current.get());
        }
        // The order is locked and shipped, so the conditional update applies.
        if (!trackingNumbers.correct(command.order(), number.get(), clock.instant())) {
            throw new IllegalStateException("order " + command.order() + " left SHIPPED while locked");
        }
        return new Result.Corrected(number.get());
    }
}
