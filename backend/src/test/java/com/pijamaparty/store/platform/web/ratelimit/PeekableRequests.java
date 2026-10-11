package com.pijamaparty.store.platform.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/** Wraps a request as the rate-limit filter does, for the key-resolver tests of other modules. */
public final class PeekableRequests {

    private PeekableRequests() {
    }

    public static HttpServletRequest asFiltered(HttpServletRequest request) {
        return new PeekableRequest(request);
    }
}
