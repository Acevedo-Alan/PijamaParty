package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-03: the available stock that the catalog shows. */
class StockAvailabilityServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");

    private final InMemoryStockStore store = new InMemoryStockStore();
    private final StockAvailabilityService service = new StockAvailabilityService(store);

    @Test
    void givenVariantsWithAndWithoutAStockLevel_whenAsked_thenEveryOneIsAnsweredAndThoseWithoutHave0() {
        UUID stocked = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        store.open(VariantId.of(stocked), OPENED);
        store.open(VariantId.of(empty), OPENED);
        StockLevel level = store.lockByVariantId(VariantId.of(stocked)).orElseThrow();
        level.adjust(MovementId.of(UUID.randomUUID()), 3, Optional.empty(), UserId.of(UUID.randomUUID()),
            AdjustmentKey.of(UUID.randomUUID()), OPENED.plusSeconds(60));
        store.save(level);

        assertThat(service.available(List.of(stocked, empty, unknown)))
            .isEqualTo(Map.of(stocked, 3, empty, 0, unknown, 0));
    }

    @Test
    void givenNoVariants_whenAsked_thenTheAnswerIsEmpty() {
        assertThat(service.available(List.of())).isEmpty();
    }
}
