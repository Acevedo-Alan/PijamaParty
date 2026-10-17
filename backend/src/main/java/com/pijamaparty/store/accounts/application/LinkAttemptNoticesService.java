package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.LinkAttemptNotices;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-27 (T-E4-14): the recipient of the link attempt notice, read in the caller's transaction. */
@Service
class LinkAttemptNoticesService implements LinkAttemptNotices {

    private final LoadUser users;

    LinkAttemptNoticesService(LoadUser users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipient> recipientOf(UUID userId) {
        return users.findById(UserId.of(userId))
            .filter(user -> user.role() == UserRole.CUSTOMER)
            .map(customer -> new Recipient(customer.email().value(), customer.name()));
    }
}
