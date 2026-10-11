package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Marks a catalog request as a search (UC-26): it carries the text parameter {@code q}, on the listing or on the
 * suggestions. The search policy keys it together with the IP (RNF-22), so a search also counts for the catalog
 * policy and browsing without text only counts there. The text itself is not part of the key.
 */
@Component
class SearchKeyResolver implements RateLimitKeyResolver {

    static final String QUERY_PARAMETER = "q";

    @Override
    public String name() {
        return "search";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        return request.getParameter(QUERY_PARAMETER) == null ? Optional.empty() : Optional.of(QUERY_PARAMETER);
    }
}
