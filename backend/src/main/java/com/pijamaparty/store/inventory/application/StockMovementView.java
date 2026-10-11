package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** A movement of the stock history (UC-07): date, user, quantity and the note shown as its reason. */
public record StockMovementView(MovementId id, MovementType type, int quantity, Optional<String> note,
                                Optional<UserId> userId, Optional<String> userName, Instant createdAt) {

    /** {@code names} has the names of the users the caller can see. */
    static StockMovementView of(StockMovement movement, Map<UserId, String> names) {
        return new StockMovementView(movement.id(), movement.type(), movement.quantity(),
            movement.note().map(MovementNote::value), movement.userId(), movement.userId().map(names::get),
            movement.createdAt());
    }
}
