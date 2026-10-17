package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.ConfirmExternalLink;
import com.pijamaparty.store.accounts.application.RequestExternalLinkConfirmation;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-27 (T-E4-14): what the storefront needs around the sign-in with Google and Facebook, which Spring Security drives
 * itself ({@link ExternalSignInFlow}): the enabled providers, the email of an external account that came without one,
 * and the confirmation of a link. Only translates between HTTP, the session and the use cases.
 */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Customer account")
class ExternalSignInController {

    private final ExternalProviderRegistrations registrations;
    private final RequestExternalLinkConfirmation requestLinkConfirmation;
    private final ConfirmExternalLink confirmLink;
    private final Clock clock;

    ExternalSignInController(ExternalProviderRegistrations registrations,
                             RequestExternalLinkConfirmation requestLinkConfirmation, ConfirmExternalLink confirmLink,
                             Clock clock) {
        this.registrations = registrations;
        this.requestLinkConfirmation = requestLinkConfirmation;
        this.confirmLink = confirmLink;
        this.clock = clock;
    }

    @GetMapping("/oauth2/providers")
    @Operation(summary = "The providers a customer can sign in with",
        description = "Each one is enabled by its credentials. Its sign-in starts at "
            + "/api/account/oauth2/authorization/{provider} (google or facebook, a browser navigation) and comes back "
            + "to the storefront.")
    @ApiResponse(responseCode = "200", description = "The enabled providers, possibly none")
    SignInProvidersResponse providers() {
        return new SignInProvidersResponse(registrations.enabled());
    }

    @PostMapping("/oauth2/email")
    @Operation(summary = "Give the email of an external account that came without one",
        description = "For the external account waiting in this session (15 minutes): an account with that email gets "
            + "the email that confirms linking it, whose link lasts 24 hours from the sending; without one, an "
            + "account is created that nobody can sign in to until the link is confirmed. The answer is the same "
            + "whether the email belongs to an account or not. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Always for a valid email; the session forgets the external account")
    @ApiResponse(responseCode = "410", description = "EXTERNAL_SIGN_IN_EXPIRED: no external account waits in this "
        + "session; sign in with the provider again",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_EMAIL",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Over 3 registrations, password recoveries and these emails per "
        + "hour from this IP (RNF-22); Retry-After says when to retry",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> giveEmail(@Valid @RequestBody ExternalAccountEmailRequest body, HttpServletRequest request) {
        Optional<PendingExternalSignIn> pending = PendingExternalSignIn.of(request, clock.instant());
        if (pending.isEmpty()) {
            return problem(HttpStatus.GONE, "No external sign-in waiting", "EXTERNAL_SIGN_IN_EXPIRED");
        }
        var command = new RequestExternalLinkConfirmation.Command(pending.get().account(),
            pending.get().providerName(), body.email());
        return switch (requestLinkConfirmation.execute(command)) {
            case RequestExternalLinkConfirmation.Result.Requested requested -> {
                PendingExternalSignIn.clear(request);
                yield ResponseEntity.noContent().build();
            }
            case RequestExternalLinkConfirmation.Result.InvalidEmail invalid ->
                problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid email", "INVALID_EMAIL");
        };
    }

    @PostMapping("/link/confirm")
    @Operation(summary = "Confirm linking an external account with the token of its link",
        description = "Uses the token, once, links the external account and verifies the email; an unverified "
            + "account loses its password. Does not sign in: the customer signs in with the provider afterwards. "
            + "Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Linked, now or by an earlier confirmation")
    @ApiResponse(responseCode = "409", description = "EXTERNAL_ACCOUNT_ALREADY_LINKED: the external account belongs to "
        + "someone else, or this account already has another one of that provider",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "410", description = "INVALID_LINK: unknown, used or expired; does not say which",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> confirmLink(@Valid @RequestBody ConfirmLinkRequest body) {
        return switch (confirmLink.execute(new ConfirmExternalLink.Command(body.token()))) {
            case ConfirmExternalLink.Result.Linked linked -> ResponseEntity.noContent().build();
            case ConfirmExternalLink.Result.InvalidLink invalid ->
                problem(HttpStatus.GONE, "Invalid or expired link", "INVALID_LINK");
            case ConfirmExternalLink.Result.AlreadyLinked alreadyLinked -> problem(HttpStatus.CONFLICT,
                "External account already linked", "EXTERNAL_ACCOUNT_ALREADY_LINKED");
        };
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String code) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return ResponseEntity.status(status).body(problem);
    }

    record SignInProvidersResponse(List<ExternalProvider> providers) {}

    /** Generous on purpose: the use case says whether it can be an email. */
    record ExternalAccountEmailRequest(@NotNull @Size(max = 320) String email) {

        @Override
        public String toString() {
            return "ExternalAccountEmailRequest[***]";
        }
    }

    /** Any value that is not a token of a link is simply an invalid link. */
    record ConfirmLinkRequest(@NotNull @Size(max = 256) String token) {

        @Override
        public String toString() {
            return "ConfirmLinkRequest[***]";
        }
    }
}
