package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListStockMovementsService implements ListStockMovements {

    private final StockHistory history;
    private final UserNameLookup userNames;

    ListStockMovementsService(StockHistory history, UserNameLookup userNames) {
        this.history = history;
        this.userNames = userNames;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        OptionalInt onHand = history.onHand(query.variantId());
        if (onHand.isEmpty()) {
            return new Result.UnknownVariant();
        }
        // One more than a page tells whether another page follows.
        List<StockMovement> rows = history.movements(query.variantId(), Math.multiplyExact(query.page(), PAGE_SIZE),
            PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<StockMovement> page = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        Set<UserId> users = page.stream().flatMap(movement -> movement.userId().stream()).collect(Collectors.toSet());
        Map<UserId, String> names = users.isEmpty() ? Map.of() : userNames.namesOf(users);
        return new Result.Listed(onHand.getAsInt(),
            page.stream().map(movement -> StockMovementView.of(movement, names)).toList(), hasMore);
    }
}
