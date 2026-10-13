package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.TrackingLinks;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.application.TokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-17 (T-E3-09): mints a tracking token for an email. Only its hash is kept (RNF-29.6); the token goes back to the
 * caller, which puts it in the link.
 */
@Service
class IssueTrackingLinkService implements TrackingLinks {

    private final TokenGenerator tokens;
    private final SaveTrackingLinks links;
    private final Clock clock;

    IssueTrackingLinkService(TokenGenerator tokens, SaveTrackingLinks links, Clock clock) {
        this.tokens = tokens;
        this.links = links;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TrackingToken issue(UUID orderId) {
        var token = new TrackingToken(tokens.newToken());
        if (!links.add(OrderId.of(orderId), token.hash(), clock.instant())) {
            throw new IllegalArgumentException("no order " + orderId + " to issue a tracking link for");
        }
        return token;
    }
}
