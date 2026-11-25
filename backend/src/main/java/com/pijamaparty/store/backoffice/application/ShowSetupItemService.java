package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Not in the activity log (UC-37), as omitting is not (UC-46). */
@Service
class ShowSetupItemService implements ShowSetupItem {

    private final SaveOmittedSetupItems omittedItems;

    ShowSetupItemService(SaveOmittedSetupItems omittedItems) {
        this.omittedItems = omittedItems;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        omittedItems.show(command.item());
        return new Result.Shown();
    }
}
