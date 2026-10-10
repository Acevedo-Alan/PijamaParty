package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.inventory.application.AddStockLevelsContractTest.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenStockLevelsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");

    private final InMemoryAddStockLevels levels = new InMemoryAddStockLevels();
    private final OpenStockLevelsService service = new OpenStockLevelsService(levels, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenNewVariants_whenTheirStockIsOpened_thenEachStartsAt0() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        service.open(List.of(first, second));

        assertThat(levels.levelOf(first)).contains(new Level(0, NOW));
        assertThat(levels.levelOf(second)).contains(new Level(0, NOW));
    }

    @Test
    void givenNoVariants_whenOpening_thenNothingIsAdded() {
        service.open(List.of());

        assertThat(levels.size()).isZero();
    }
}
