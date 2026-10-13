package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake of {@link ReserveOrderStock}: the units available of each variant and what each order reserved. */
public class InMemoryReserveOrderStock implements ReserveOrderStock {

    private final Map<UUID, Integer> available = new HashMap<>();
    private final Map<OrderId, List<OrderLine>> reservations = new HashMap<>();

    /** Test arrangement: {@code units} available of the variant. */
    public synchronized void stock(UUID variantId, int units) {
        available.put(variantId, units);
    }

    /** Test inspection: on hand − reserved. */
    public synchronized int availableOf(UUID variantId) {
        return available.getOrDefault(variantId, 0);
    }

    /** Test inspection: what the order reserved, empty when nothing. */
    public synchronized List<OrderLine> reservedBy(OrderId order) {
        return reservations.getOrDefault(order, List.of());
    }

    @Override
    public synchronized Optional<Shortage> reserve(OrderId order, List<OrderLine> lines) {
        if (reservations.containsKey(order)) {
            return Optional.empty();
        }
        // In variant id order, as the inventory reserves them; uuids compare as PostgreSQL orders them.
        Optional<Shortage> shortage = lines.stream()
            .sorted(Comparator.comparing(line -> line.variantId().toString()))
            .filter(line -> availableOf(line.variantId()) < line.quantity())
            .findFirst()
            .map(line -> new Shortage(line.variantId(), availableOf(line.variantId())));
        if (shortage.isEmpty()) {
            lines.forEach(line -> available.merge(line.variantId(), -line.quantity(), Integer::sum));
            reservations.put(order, List.copyOf(lines));
        }
        return shortage;
    }
}
