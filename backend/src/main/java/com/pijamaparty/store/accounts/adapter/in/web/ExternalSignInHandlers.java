package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.ConcurrentExternalSignInException;
import com.pijamaparty.store.accounts.application.LinkExternalAccount;
import com.pijamaparty.store.accounts.application.SignInWithExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.security.SessionAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * UC-27 (T-E4-14): where the browser goes back to after the provider. Spring Security already checked the
 * {@code state}, exchanged the code with PKCE and, for Google, validated the ID token with its {@code nonce}. This
 * adapter only translates the provider's account into the use case's command and its result into the customer's own
 * session and a page of the storefront, on its own origin ({@code store.public-url}), whatever host the request came
 * through. The provider's authentication never becomes the session. T-E4-15: when the signed-in customer asked to
 * link that provider from "Mi cuenta" ({@link PendingExternalLink}), the return links instead and goes back there.
 */
@Component
class ExternalSignInHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    static final String SIGNED_IN = "/";
    static final String EMAIL_REQUIRED = "/cuenta/completar-email";
    private static final String SIGN_IN_PAGE = "/cuenta/ingresar?social=";
    static final String LINK_CONFIRMATION_SENT = SIGN_IN_PAGE + "confirm-link";
    static final String ALREADY_LINKED = SIGN_IN_PAGE + "already-linked";
    static final String CANCELLED = SIGN_IN_PAGE + "cancelled";
    static final String FAILED = SIGN_IN_PAGE + "failed";
    /** T-E4-15: where a link started from "Mi cuenta" comes back to. */
    private static final String ACCOUNT_PAGE = "/cuenta?social=";
    static final String LINKED = ACCOUNT_PAGE + "linked";
    static final String LINK_ALREADY_LINKED = ACCOUNT_PAGE + "already-linked";
    static final String LINK_CANCELLED = ACCOUNT_PAGE + "cancelled";
    static final String LINK_FAILED = ACCOUNT_PAGE + "failed";

    private static final Logger log = LoggerFactory.getLogger(ExternalSignInHandlers.class);

    private final SignInWithExternalAccount signIn;
    private final LinkExternalAccount link;
    private final SessionAuthentication sessions;
    private final Clock clock;
    private final String storefront;

    ExternalSignInHandlers(SignInWithExternalAccount signIn, LinkExternalAccount link, SessionAuthentication sessions,
                           Clock clock, @Value("${store.public-url}") URI publicUrl) {
        this.signIn = signIn;
        this.link = link;
        this.sessions = sessions;
        this.clock = clock;
        this.storefront = publicUrl.toString().replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SecurityContextHolder.clearContext();
        Optional<PendingExternalLink> pendingLink = pendingLink(request);
        Optional<SignInWithExternalAccount.Command> command = authentication instanceof OAuth2AuthenticationToken token
            ? command(token) : Optional.empty();
        if (pendingLink.isPresent()) {
            response.sendRedirect(storefront + link(pendingLink.get(), command));
            return;
        }
        if (command.isEmpty()) {
            response.sendRedirect(storefront + FAILED);
            return;
        }
        String target;
        try {
            target = signIn(command.get(), request, response);
        } catch (ConcurrentExternalSignInException e) {
            target = FAILED;
        } catch (RuntimeException e) {
            // A failing sign-in listener leaves no session open, as with the password; no personal data is logged.
            log.error("Sign-in with {} failed", command.get().account().provider(), e);
            SecurityContextHolder.clearContext();
            target = FAILED;
        }
        response.sendRedirect(storefront + target);
    }

    private String signIn(SignInWithExternalAccount.Command command, HttpServletRequest request,
                          HttpServletResponse response) {
        return switch (signIn.execute(command)) {
            case SignInWithExternalAccount.Result.SignedIn signedIn -> {
                PendingExternalSignIn.clear(request);
                sessions.signIn(signedIn.customer(), Role.CUSTOMER, request, response);
                yield SIGNED_IN;
            }
            case SignInWithExternalAccount.Result.EmailRequired required -> {
                PendingExternalSignIn.of(command.account(), command.name(), clock.instant()).store(request);
                yield EMAIL_REQUIRED;
            }
            case SignInWithExternalAccount.Result.LinkConfirmationSent sent -> LINK_CONFIRMATION_SENT;
            case SignInWithExternalAccount.Result.ProviderAlreadyLinked linked -> ALREADY_LINKED;
            case SignInWithExternalAccount.Result.Refused refused -> FAILED;
        };
    }

    /**
     * T-E4-15: the link this session asked for from "Mi cuenta", forgotten either way. It counts only while the
     * customer that asked for it is still the one signed in; otherwise the return is a sign-in.
     */
    private Optional<PendingExternalLink> pendingLink(HttpServletRequest request) {
        Optional<PendingExternalLink> pending = PendingExternalLink.take(request, clock.instant());
        Optional<UserId> customer = sessions.signedIn(Role.CUSTOMER, request);
        return pending.filter(link -> customer.equals(Optional.of(link.customerId())));
    }

    /** The session stays as it was: the customer is already signed in. */
    private String link(PendingExternalLink pending, Optional<SignInWithExternalAccount.Command> returned) {
        Optional<SignInWithExternalAccount.Command> command = returned
            .filter(each -> each.account().provider() == pending.provider());
        if (command.isEmpty()) {
            return LINK_FAILED;
        }
        var linkCommand = new LinkExternalAccount.Command(pending.customerId(), command.get().account(),
            command.get().email(), command.get().emailVerified());
        try {
            return switch (link.execute(linkCommand)) {
                case LinkExternalAccount.Result.Linked linked -> LINKED;
                case LinkExternalAccount.Result.AlreadyLinked alreadyLinked -> LINK_ALREADY_LINKED;
                case LinkExternalAccount.Result.NotFound notFound -> LINK_FAILED;
            };
        } catch (RuntimeException e) {
            // No personal data is logged.
            log.error("Linking {} failed", pending.provider(), e);
            return LINK_FAILED;
        }
    }

    /** The visitor cancelled at the provider ({@code access_denied}), or anything else went wrong: nothing changes. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        boolean cancelled = exception instanceof OAuth2AuthenticationException oauth2
            && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth2.getError().getErrorCode());
        if (pendingLink(request).isPresent()) {
            response.sendRedirect(storefront + (cancelled ? LINK_CANCELLED : LINK_FAILED));
            return;
        }
        response.sendRedirect(storefront + (cancelled ? CANCELLED : FAILED));
    }

    /**
     * Google: the claims of its validated ID token, whose {@code email_verified} counts. Facebook: {@code id},
     * {@code email} and {@code name} of its Graph API; it never says whether the email is verified.
     */
    static Optional<SignInWithExternalAccount.Command> command(OAuth2AuthenticationToken token) {
        Optional<ExternalProvider> provider = ExternalProviderRegistrations.provider(
            token.getAuthorizedClientRegistrationId());
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        OAuth2User user = token.getPrincipal();
        if (user instanceof OidcUser oidc) {
            return ExternalAccount.of(provider.get(), oidc.getSubject()).map(account ->
                new SignInWithExternalAccount.Command(account, text(oidc.getEmail()),
                    Boolean.TRUE.equals(oidc.getEmailVerified()), text(oidc.getFullName())));
        }
        return ExternalAccount.of(provider.get(), text(user.getAttribute("id")).orElse(null)).map(account ->
            new SignInWithExternalAccount.Command(account, text(user.getAttribute("email")), false,
                text(user.getAttribute("name"))));
    }

    /** A text the provider returned; empty when absent, blank or of another type. */
    private static Optional<String> text(Object value) {
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }
}
