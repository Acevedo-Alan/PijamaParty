package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.ordering.application.OrderStatuses.LockedOrder;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CorrectShipmentTrackingNumberService implements CorrectShipmentTrackingNumber {

    private final OrderStatuses orderStatuses;
    private final ShipmentTrackingNumbers trackingNumbers;
    private final OrderActivity activity;
    private final Clock clock;

    CorrectShipmentTrackingNumberService(OrderStatuses orderStatuses, ShipmentTrackingNumbers trackingNumbers,
                                         OrderActivity activity, Clock clock) {
        this.orderStatuses = orderStatuses;
        this.trackingNumbers = trackingNumbers;
        this.activity = activity;
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
        Optional<LockedOrder> locked = orderStatuses.lockForChange(command.order());
        if (locked.isEmpty()) {
            return new Result.NotFound();
        }
        LockedOrder order = locked.get();
        if (!StatusChange.allowsTrackingNumberCorrection(order.status())) {
            return new Result.NotShipped(order.status());
        }
        // The order is locked and shipped, so the conditional update applies.
        if (!trackingNumbers.correct(command.order(), number.get(), clock.instant())) {
            throw new IllegalStateException("order " + command.order() + " left SHIPPED while locked");
        }
        String before = order.trackingNumber().map(ShipmentTrackingNumber::value).orElse(null);
        // UC-37: the same number again changes nothing worth recording.
        if (!number.get().value().equals(before)) {
            activity.record(new ActivityEntry(command.actor().userId().orElseThrow(),
                ActivityAction.TRACKING_NUMBER_CORRECTED,
                ActivitySubject.of(SubjectType.ORDER, command.order().value(), order.label()),
                List.of(FieldChange.of("trackingNumber", before, number.get().value()))));
        }
        return new Result.Corrected(number.get());
    }
}
