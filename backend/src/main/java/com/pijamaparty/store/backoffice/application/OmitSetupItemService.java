package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Not in the activity log (UC-37): it changes what the guide shows the owner, not the store (UC-46). */
@Service
class OmitSetupItemService implements OmitSetupItem {

    private final SaveOmittedSetupItems omittedItems;
    private final Clock clock;

    OmitSetupItemService(SaveOmittedSetupItems omittedItems, Clock clock) {
        this.omittedItems = omittedItems;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        if (!command.item().omittable()) {
            return new Result.NotOmittable();
        }
        omittedItems.omit(command.item(), clock.instant());
        return new Result.Omitted();
    }
}
