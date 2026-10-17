package com.pijamaparty.store.ordering.adapter.in.signin;

import com.pijamaparty.store.ordering.application.LinkGuestOrders;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.security.SignInListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * UC-22 (T-E4-08): a customer who signs in gets the guest orders of its verified email linked to its account, as
 * {@code SYSTEM}: the session is not open yet, and the database lets only the system link an order (V33). The
 * administrator's sign-in links nothing.
 */
@Component
class GuestOrdersSignIn implements SignInListener {

    private final LinkGuestOrders link;

    GuestOrdersSignIn(LinkGuestOrders link) {
        this.link = link;
    }

    @Override
    public void signingIn(UserId userId, Role role, HttpServletRequest request, HttpServletResponse response) {
        if (role != Role.CUSTOMER) {
            return;
        }
        CurrentActor.callAsSystem(() -> link.execute(new LinkGuestOrders.Command(userId)));
    }
}
