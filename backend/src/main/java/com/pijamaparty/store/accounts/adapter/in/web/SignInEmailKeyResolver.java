package com.pijamaparty.store.accounts.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.platform.web.ratelimit.PeekableRequest;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The account of a sign-in, for the per-account login limit of RNF-29.5: the normalized email of the body, the same
 * normalization the sign-in uses. It does not look the account up, so the {@code 429} never reveals whether it
 * exists. The key is a SHA-256 of the email, so not even the limiter's memory holds it in clear.
 *
 * <p>A body without a usable email (too large, not JSON, no email) shares one bucket instead of escaping the limit:
 * the back office never sends one, so only such requests compete for it.
 */
@Component
class SignInEmailKeyResolver implements RateLimitKeyResolver {

    static final String UNREADABLE = "unreadable";

    private final ObjectMapper objectMapper;

    SignInEmailKeyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "sign-in-email";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        return Optional.of(email(request).map(SignInEmailKeyResolver::sha256).orElse(UNREADABLE));
    }

    private Optional<Email> email(HttpServletRequest request) {
        try {
            Optional<byte[]> body = PeekableRequest.body(request);
            if (body.isEmpty()) {
                return Optional.empty();
            }
            JsonNode email = objectMapper.readTree(body.get()).path("email");
            return email.isTextual() ? Email.parse(email.textValue()) : Optional.empty();
        } catch (IOException notReadable) {
            return Optional.empty();
        }
    }

    private static String sha256(Email email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.value().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
