package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.adapter.in.web.AddressBookController.SavedAddressResponse;
import com.pijamaparty.store.accounts.adapter.in.web.SignInMethodsController.IdentityResponse;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.accounts.application.CustomerDataExport;
import com.pijamaparty.store.accounts.application.DeleteCustomerAccount;
import com.pijamaparty.store.accounts.application.ExportCustomerData;
import com.pijamaparty.store.accounts.application.RequestReauthentication;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.security.SessionAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-28 (T-E4-16): "Mi cuenta", the customer's data: downloading it, and deleting the account, confirmed with the
 * password or by signing in again with a linked provider ({@link ExternalSignInHandlers} checks the return). Only
 * translates between HTTP, the session and the use cases.
 */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Customer account")
class CustomerDataController {

    static final String FILE_NAME = "mis-datos.json";

    private final ExportCustomerData export;
    private final DeleteCustomerAccount delete;
    private final RequestReauthentication reauthentication;
    private final ExternalProviderRegistrations registrations;
    private final SessionAuthentication sessions;
    private final Clock clock;

    CustomerDataController(ExportCustomerData export, DeleteCustomerAccount delete,
                           RequestReauthentication reauthentication,
                           ExternalProviderRegistrations registrations, SessionAuthentication sessions, Clock clock) {
        this.export = export;
        this.delete = delete;
        this.reauthentication = reauthentication;
        this.registrations = registrations;
        this.sessions = sessions;
        this.clock = clock;
    }

    @GetMapping("/data")
    @Operation(summary = "Download the customer's data",
        description = "One JSON file with the profile, the linked external accounts, the addresses, the orders and "
            + "the reviews; every section is present, empty when there is nothing.")
    @ApiResponse(responseCode = "200", description = "The file (Content-Disposition: attachment)",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerDataResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> data() {
        return switch (export.execute(actor())) {
            case ExportCustomerData.Result.Exported exported -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(FILE_NAME).build().toString())
                .body(CustomerDataResponse.of(exported.data()));
            case ExportCustomerData.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @PostMapping("/oauth2/reauthenticate/{provider}")
    @Operation(summary = "Ask to confirm the account's deletion with a linked provider (google or facebook)",
        description = "For 15 minutes this session confirms, instead of signing in, when it comes back from that "
            + "provider; the storefront then navigates to /api/account/oauth2/authorization/{provider}. Coming back "
            + "with the external account linked to the customer lets this session delete the account without the "
            + "password for 5 minutes: /cuenta?social=reauthenticated; otherwise reauthentication-failed or "
            + "reauthentication-cancelled. Requires the "
            + "CSRF token.")
    @ApiResponse(responseCode = "204", description = "The session waits for the provider's return")
    @ApiResponse(responseCode = "404", description = "PROVIDER_NOT_ENABLED, or IDENTITY_NOT_FOUND: the customer has no "
        + "account of that provider",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> requestReauthentication(@PathVariable String provider, HttpServletRequest request) {
        Actor actor = actor();
        Optional<ExternalProvider> enabled = ExternalProviderRegistrations.provider(provider)
            .filter(each -> registrations.findByRegistrationId(provider) != null);
        if (enabled.isEmpty()) {
            return problem(HttpStatus.NOT_FOUND, "Provider not enabled", "PROVIDER_NOT_ENABLED");
        }
        return switch (reauthentication.execute(new RequestReauthentication.Command(actor, enabled.get()))) {
            case RequestReauthentication.Result.Requested requested -> {
                PendingProviderIntent.of(actor.userId().orElseThrow(), enabled.get(),
                    PendingProviderIntent.Purpose.REAUTHENTICATE, clock.instant()).store(request);
                yield ResponseEntity.noContent().build();
            }
            case RequestReauthentication.Result.NotLinked notLinked ->
                problem(HttpStatus.NOT_FOUND, "External account not linked", "IDENTITY_NOT_FOUND");
            case RequestReauthentication.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @DeleteMapping
    @Operation(summary = "Delete the customer's account",
        description = "Confirmed with the password, or without it within 5 minutes of coming back from a linked "
            + "provider. Deletes the account, its addresses, its linked external accounts and its cart; the orders "
            + "stay, unlinked from the account. Signs out, and every other session of the account ends. Requires the "
            + "CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted and signed out")
    @ApiResponse(responseCode = "403", description = "WRONG_PASSWORD, or REAUTHENTICATION_REQUIRED: neither a password "
        + "nor a recent confirmation with a provider",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Over 5 deletion attempts per minute from this IP (RNF-22); "
        + "Retry-After says when to retry",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> deleteAccount(@Valid @RequestBody(required = false) DeleteAccountRequest body,
                                    HttpServletRequest request, HttpServletResponse response) {
        Actor actor = actor();
        Optional<String> password = Optional.ofNullable(body).map(DeleteAccountRequest::password);
        DeleteCustomerAccount.Confirmation confirmation;
        if (password.isPresent()) {
            confirmation = new DeleteCustomerAccount.Confirmation.Password(password.get());
        } else if (actor.userId().filter(customer ->
            RecentReauthentication.holds(request, customer, clock.instant())).isPresent()) {
            confirmation = new DeleteCustomerAccount.Confirmation.Reauthenticated();
        } else {
            confirmation = new DeleteCustomerAccount.Confirmation.None();
        }
        return switch (delete.execute(new DeleteCustomerAccount.Command(actor, confirmation))) {
            case DeleteCustomerAccount.Result.Deleted deleted -> {
                sessions.signOut(request, response);
                yield ResponseEntity.noContent().build();
            }
            case DeleteCustomerAccount.Result.WrongPassword wrong ->
                problem(HttpStatus.FORBIDDEN, "Wrong password", "WRONG_PASSWORD");
            case DeleteCustomerAccount.Result.ConfirmationRequired required ->
                problem(HttpStatus.FORBIDDEN, "Reauthentication required", "REAUTHENTICATION_REQUIRED");
            case DeleteCustomerAccount.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
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

    /** Personal data, the owner's own: {@link #toString()} never prints it (R-05). */
    record CustomerDataResponse(Instant exportedAt, ProfileResponse profile, List<IdentityResponse> identities,
                                List<SavedAddressResponse> addresses, List<?> orders, List<?> reviews) {

        static CustomerDataResponse of(CustomerDataExport data) {
            var profile = data.profile();
            return new CustomerDataResponse(data.exportedAt(),
                new ProfileResponse(profile.email(), profile.name(), profile.phone().orElse(null),
                    profile.emailVerified(), data.signInMethods().hasPassword(), profile.createdAt()),
                data.signInMethods().identities().stream()
                    .map(identity -> new IdentityResponse(identity.provider(), identity.email(), identity.linkedAt()))
                    .toList(),
                data.addresses().stream().map(SavedAddressResponse::of).toList(),
                data.section(CustomerDataSection.ORDERS), data.section(CustomerDataSection.REVIEWS));
        }

        @Override
        public String toString() {
            return "CustomerDataResponse[exportedAt=" + exportedAt + ", ***]";
        }
    }

    record ProfileResponse(String email, String name,
                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String phone,
                           boolean emailVerified, boolean hasPassword, Instant createdAt) {

        @Override
        public String toString() {
            return "ProfileResponse[***]";
        }
    }

    /** No body, or no password: the session must hold a recent confirmation with a provider. */
    record DeleteAccountRequest(@Size(max = 1024) String password) {

        @Override
        public String toString() {
            return "DeleteAccountRequest[***]";
        }
    }
}
