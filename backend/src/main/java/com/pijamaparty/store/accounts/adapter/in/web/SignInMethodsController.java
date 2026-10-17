package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.CreatePassword;
import com.pijamaparty.store.accounts.application.ListSignInMethods;
import com.pijamaparty.store.accounts.application.SignInMethodsView;
import com.pijamaparty.store.accounts.application.UnlinkExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-27 (T-E4-15): "Mi cuenta", how the signed-in customer signs in: its linked external accounts, unlinking one,
 * creating a password, and asking to link a provider before the storefront navigates to it ({@link
 * ExternalSignInHandlers} links on the return). Only translates between HTTP, the session and the use cases.
 */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Customer account")
class SignInMethodsController {

    private final ListSignInMethods list;
    private final UnlinkExternalAccount unlink;
    private final CreatePassword createPassword;
    private final ExternalProviderRegistrations registrations;
    private final Clock clock;

    SignInMethodsController(ListSignInMethods list, UnlinkExternalAccount unlink, CreatePassword createPassword,
                            ExternalProviderRegistrations registrations, Clock clock) {
        this.list = list;
        this.unlink = unlink;
        this.createPassword = createPassword;
        this.registrations = registrations;
        this.clock = clock;
    }

    @GetMapping("/identities")
    @Operation(summary = "How the customer signs in",
        description = "Whether it has a password, and its linked external accounts, Google first, with the email "
            + "each one was linked with.")
    @ApiResponse(responseCode = "200", description = "The sign-in methods",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = SignInMethodsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> signInMethods() {
        return switch (list.execute(actor())) {
            case ListSignInMethods.Result.Listed listed -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SignInMethodsResponse.of(listed.methods()));
            case ListSignInMethods.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @DeleteMapping("/identities/{provider}")
    @Operation(summary = "Unlink the customer's account of a provider (google or facebook)",
        description = "Refused when it is the last way in: no password and no other provider. Requires the CSRF "
            + "token.")
    @ApiResponse(responseCode = "204", description = "Unlinked")
    @ApiResponse(responseCode = "404", description = "IDENTITY_NOT_FOUND: no account of that provider is linked",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "LAST_SIGN_IN_METHOD: create a password or link another provider "
        + "first",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> unlink(@PathVariable String provider) {
        Optional<ExternalProvider> known = ExternalProviderRegistrations.provider(provider);
        if (known.isEmpty()) {
            return identityNotFound();
        }
        return switch (unlink.execute(new UnlinkExternalAccount.Command(actor(), known.get()))) {
            case UnlinkExternalAccount.Result.Unlinked unlinked -> ResponseEntity.noContent().build();
            case UnlinkExternalAccount.Result.NotLinked notLinked -> identityNotFound();
            case UnlinkExternalAccount.Result.LastSignInMethod last ->
                problem(HttpStatus.CONFLICT, "Last sign-in method", "LAST_SIGN_IN_METHOD");
            case UnlinkExternalAccount.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @PutMapping("/password")
    @Operation(summary = "Create the password of an account without one",
        description = "Only creates: an existing password is changed through the password recovery. Does not verify "
            + "the email. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Created")
    @ApiResponse(responseCode = "409", description = "PASSWORD_ALREADY_SET",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "PASSWORD_TOO_SHORT (under 8 characters) or PASSWORD_TOO_LONG "
        + "(over 72 bytes)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> createPassword(@Valid @RequestBody CreatePasswordRequest body) {
        return switch (createPassword.execute(new CreatePassword.Command(actor(), body.password()))) {
            case CreatePassword.Result.Created created -> ResponseEntity.noContent().build();
            case CreatePassword.Result.AlreadySet alreadySet ->
                problem(HttpStatus.CONFLICT, "Password already set", "PASSWORD_ALREADY_SET");
            case CreatePassword.Result.InvalidPassword invalid -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid password", invalid.violation() == PasswordRules.Violation.TOO_SHORT
                    ? "PASSWORD_TOO_SHORT" : "PASSWORD_TOO_LONG");
            case CreatePassword.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @PostMapping("/oauth2/link/{provider}")
    @Operation(summary = "Ask to link an account of a provider (google or facebook)",
        description = "For 15 minutes this session links, instead of signing in, when it comes back from that "
            + "provider; the storefront then navigates to /api/account/oauth2/authorization/{provider}. The provider "
            + "comes back to /cuenta?social=linked, already-linked, cancelled or failed. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "The session waits for the provider's return")
    @ApiResponse(responseCode = "404", description = "PROVIDER_NOT_ENABLED",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> requestLink(@PathVariable String provider, HttpServletRequest request) {
        Optional<ExternalProvider> enabled = ExternalProviderRegistrations.provider(provider)
            .filter(each -> registrations.findByRegistrationId(provider) != null);
        Optional<Actor> actor = CurrentActor.get();
        if (enabled.isEmpty() || actor.flatMap(Actor::userId).isEmpty()) {
            return problem(HttpStatus.NOT_FOUND, "Provider not enabled", "PROVIDER_NOT_ENABLED");
        }
        PendingProviderIntent.of(actor.get().userId().get(), enabled.get(), PendingProviderIntent.Purpose.LINK,
            clock.instant()).store(request);
        return ResponseEntity.noContent().build();
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> identityNotFound() {
        return problem(HttpStatus.NOT_FOUND, "External account not linked", "IDENTITY_NOT_FOUND");
    }

    private static ResponseEntity<ProblemDetail> notSignedIn() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String code) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return ResponseEntity.status(status).body(problem);
    }

    record SignInMethodsResponse(boolean hasPassword, List<IdentityResponse> identities) {

        static SignInMethodsResponse of(SignInMethodsView view) {
            return new SignInMethodsResponse(view.hasPassword(), view.identities().stream()
                .map(identity -> new IdentityResponse(identity.provider(), identity.email(), identity.linkedAt()))
                .toList());
        }
    }

    record IdentityResponse(ExternalProvider provider, String email, Instant linkedAt) {

        @Override
        public String toString() {
            return "IdentityResponse[provider=" + provider + ", ***]";
        }
    }

    /** Generous on purpose: the use case gives the precise reason. */
    record CreatePasswordRequest(@NotNull @Size(max = 1024) String password) {

        @Override
        public String toString() {
            return "CreatePasswordRequest[***]";
        }
    }
}
