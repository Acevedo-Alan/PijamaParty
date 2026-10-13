package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.MovementId;
import com.pijamaparty.store.inventory.domain.MovementNote;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdjustStockService implements AdjustStock {

    private final LoadStockLevels loadStockLevels;
    private final SaveStockLevels saveStockLevels;
    private final IdGenerator ids;
    private final Clock clock;

    AdjustStockService(LoadStockLevels loadStockLevels, SaveStockLevels saveStockLevels, IdGenerator ids,
                       Clock clock) {
        this.loadStockLevels = loadStockLevels;
        this.saveStockLevels = saveStockLevels;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        if (!StockLevel.isValidAdjustment(command.quantity())) {
            return new Result.InvalidQuantity();
        }
        // An empty or blank note is no note.
        Optional<String> text = command.note().map(String::strip).filter(note -> !note.isEmpty());
        Optional<MovementNote> note = text.flatMap(MovementNote::parse);
        if (text.isPresent() && note.isEmpty()) {
            return new Result.InvalidNote();
        }
        // Locked before the key is checked, so a concurrent retry waits and then finds this adjustment's movement.
        Optional<StockLevel> found = loadStockLevels.lockByVariantId(command.variantId());
        if (found.isEmpty()) {
            return new Result.UnknownVariant();
        }
        StockLevel level = found.get();
        Optional<StockMovement> previous = loadStockLevels.findAdjustment(command.variantId(), command.key());
        if (previous.isPresent()) {
            return previous.get().repeats(command.quantity(), note)
                ? new Result.AlreadyAdjusted(level.onHand())
                : new Result.KeyReused();
        }
        var outcome = level.adjust(MovementId.of(ids.newId()), command.quantity(), note,
            command.actor().userId().orElseThrow(), command.key(), clock.instant());
        return switch (outcome) {
            case StockLevel.AdjustOutcome.Adjusted adjusted -> {
                saveStockLevels.save(level);
                yield new Result.Adjusted(adjusted.onHand());
            }
            case StockLevel.AdjustOutcome.WouldGoNegative negative ->
                new Result.WouldGoNegative(negative.currentOnHand());
            case StockLevel.AdjustOutcome.WouldGoBelowReserved belowReserved ->
                new Result.WouldGoBelowReserved(belowReserved.currentOnHand(), belowReserved.reserved());
            case StockLevel.AdjustOutcome.WouldExceedMaximum maximum ->
                new Result.WouldExceedMaximum(maximum.currentOnHand());
        };
    }
}
