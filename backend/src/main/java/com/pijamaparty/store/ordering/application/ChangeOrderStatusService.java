package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChanges;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.ordering.application.OrderStatuses.LockedOrder;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChangeOrderStatusService implements ChangeOrderStatus {

    private final OrderStatuses orderStatuses;
    private final StatusChanges statusChanges;
    private final StockReleases stockReleases;
    private final StockRestocks stockRestocks;
    private final OrderActivity activity;
    private final Clock clock;

    ChangeOrderStatusService(OrderStatuses orderStatuses, StatusChanges statusChanges,
                             StockReleases stockReleases, StockRestocks stockRestocks, OrderActivity activity,
                             Clock clock) {
        this.orderStatuses = orderStatuses;
        this.statusChanges = statusChanges;
        this.stockReleases = stockReleases;
        this.stockRestocks = stockRestocks;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<LockedOrder> locked = orderStatuses.lockForChange(command.order());
        if (locked.isEmpty()) {
            return new Result.NotFound();
        }
        if (locked.get().status() != command.from()) {
            return new Result.StatusChanged(locked.get().status());
        }
        Optional<ShipmentTrackingNumber> trackingNumber = Optional.empty();
        if (command.shipmentTrackingNumber().isPresent()) {
            trackingNumber = ShipmentTrackingNumber.parse(command.shipmentTrackingNumber().get());
            if (trackingNumber.isEmpty()) {
                return new Result.InvalidTrackingNumber();
            }
        }
        return switch (StatusChange.decide(command.from(), command.to(), trackingNumber, command.restock())) {
            case StatusChange.Decision.Allowed allowed -> apply(command, locked.get(), allowed.change());
            case StatusChange.Decision.InvalidTransition invalid -> new Result.InvalidTransition();
            case StatusChange.Decision.MissingTrackingNumber missing -> new Result.MissingTrackingNumber();
            case StatusChange.Decision.RestockDecisionRequired required -> new Result.RestockDecisionRequired();
            case StatusChange.Decision.UnexpectedField unexpected -> new Result.UnexpectedField(unexpected.field());
        };
    }

    private Result apply(Command command, LockedOrder order, StatusChange change) {
        Instant now = clock.instant();
        // RNF-20.5: the order is locked and in the status the administrator saw, so the conditional update applies.
        if (!statusChanges.change(command.order(), change, now)) {
            throw new IllegalStateException("order " + command.order() + " left " + change.from() + " while locked");
        }
        switch (change.stock()) {
            case RELEASE -> stockReleases.release(command.order());
            case RESTOCK -> stockRestocks.restock(command.order());
            case NONE -> {
            }
        }
        record(command, order, change);
        return new Result.Changed(change.to());
    }

    /** UC-37: from and to, the tracking number when shipped, and whether the stock was put back when that was asked. */
    private void record(Command command, LockedOrder order, StatusChange change) {
        FieldChanges changes = new FieldChanges()
            .add("status", change.from().name(), change.to().name())
            .add("trackingNumber", null, change.trackingNumber().map(ShipmentTrackingNumber::value).orElse(null))
            .add("restock", null, command.restock().map(String::valueOf).orElse(null));
        activity.record(new ActivityEntry(command.actor().userId().orElseThrow(), ActivityAction.ORDER_STATUS_CHANGED,
            ActivitySubject.of(SubjectType.ORDER, command.order().value(), order.label()), changes.list()));
    }
}
