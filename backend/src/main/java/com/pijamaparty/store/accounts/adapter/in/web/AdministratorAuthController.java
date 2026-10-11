package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.AdministratorView;
import com.pijamaparty.store.accounts.application.ConfirmSecondFactorEnrollment;
import com.pijamaparty.store.accounts.application.GetCurrentAdministrator;
import com.pijamaparty.store.accounts.application.SignInAdministrator;
import com.pijamaparty.store.accounts.application.StartSecondFactorEnrollment;
import com.pijamaparty.store.accounts.application.VerifySecondFactor;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-04: sign-in in two steps (password, then the second factor of T-E1-16), sign-out and current user of the back
 * office. Only translates between HTTP, the session and the use cases.
 */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Back office authentication")
@ApiResponse(responseCode = "429", description = "Over 5 attempts per minute from this IP (RNF-22); Retry-After says "
    + "when to retry",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class AdministratorAuthController {

    private final SignInAdministrator signIn;
    private final VerifySecondFactor verifySecondFactor;
    private final StartSecondFactorEnrollment startEnrollment;
    private final ConfirmSecondFactorEnrollment confirmEnrollment;
    private final GetCurrentAdministrator currentAdministrator;
    private final SessionAuthentication sessions;
    private final Clock clock;

    AdministratorAuthController(SignInAdministrator signIn, VerifySecondFactor verifySecondFactor,
                                StartSecondFactorEnrollment startEnrollment,
                                ConfirmSecondFactorEnrollment confirmEnrollment,
                                GetCurrentAdministrator currentAdministrator, SessionAuthentication sessions,
                                Clock clock) {
        this.signIn = signIn;
        this.verifySecondFactor = verifySecondFactor;
        this.startEnrollment = startEnrollment;
        this.confirmEnrollment = confirmEnrollment;
        this.currentAdministrator = currentAdministrator;
        this.sessions = sessions;
        this.clock = clock;
    }

    @PostMapping("/login")
    @Operation(summary = "First step: the administrator's email and password",
        description = "A right password never opens the session: nextStep says whether the second factor or its "
            + "enrollment comes next, within 5 minutes. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Right password; the sign-in goes on with nextStep",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = NextStepResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials; does not say which field failed",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "423", description = "Locked after 5 consecutive failures; lockedUntil says until when",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request) {
        PendingSignIn.clear(request);
        return switch (signIn.execute(new SignInAdministrator.Command(body.email(), body.password()))) {
            case SignInAdministrator.Result.PasswordAccepted accepted -> {
                PendingSignIn.after(accepted, clock.instant()).store(request);
                yield ResponseEntity.ok(new NextStepResponse(accepted.nextStep()));
            }
            case SignInAdministrator.Result.InvalidCredentials invalid -> invalidCredentials();
            case SignInAdministrator.Result.Locked locked -> locked(locked.until());
        };
    }

    @PostMapping("/second-factor")
    @Operation(summary = "Second step: a code of the authenticator app or a recovery code",
        description = "Opens the session (new session id and CSRF token). A wrong code counts as a failed sign-in. "
            + "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Signed in",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministratorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong, reused or used code",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "No pending sign-in, or it expired: start with the password",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "423", description = "Locked after 5 consecutive failures; lockedUntil says until when",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> secondFactor(@Valid @RequestBody CodeRequest body, HttpServletRequest request,
                                   HttpServletResponse response) {
        Optional<PendingSignIn> pending =
            PendingSignIn.of(request, SignInAdministrator.NextStep.SECOND_FACTOR, clock.instant());
        if (pending.isEmpty()) {
            return noPendingSignIn();
        }
        return switch (verifySecondFactor.execute(new VerifySecondFactor.Command(pending.get().user(), body.code()))) {
            case VerifySecondFactor.Result.SignedIn signedIn -> open(signedIn.administrator(), request, response,
                AdministratorResponse.of(signedIn.administrator()));
            case VerifySecondFactor.Result.InvalidCode invalid -> invalidCode();
            case VerifySecondFactor.Result.Locked locked -> {
                PendingSignIn.clear(request);
                yield locked(locked.until());
            }
            case VerifySecondFactor.Result.NotPending notPending -> {
                PendingSignIn.clear(request);
                yield noPendingSignIn();
            }
        };
    }

    @GetMapping("/enrollment")
    @Operation(summary = "Enrollment: a new secret for the authenticator app",
        description = "Only during a pending sign-in without a second factor. Each call makes a new secret.")
    @ApiResponse(responseCode = "200", description = "The secret, as text, URI and QR code",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnrollmentResponse.class)))
    @ApiResponse(responseCode = "409", description = "No pending enrollment, or it expired",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> startEnrollment(HttpServletRequest request) {
        Optional<PendingSignIn> pending =
            PendingSignIn.of(request, SignInAdministrator.NextStep.ENROLLMENT, clock.instant());
        if (pending.isEmpty()) {
            return noPendingSignIn();
        }
        return switch (startEnrollment.execute(new StartSecondFactorEnrollment.Command(pending.get().user()))) {
            case StartSecondFactorEnrollment.Result.Started started -> {
                pending.get().withEnrollmentSecret(started.secret()).store(request);
                yield ResponseEntity.ok(new EnrollmentResponse(started.secretText(), started.otpauthUri(),
                    started.qrCode()));
            }
            case StartSecondFactorEnrollment.Result.AlreadyEnrolled enrolled -> {
                PendingSignIn.clear(request);
                yield noPendingSignIn();
            }
            case StartSecondFactorEnrollment.Result.NotPending notPending -> {
                PendingSignIn.clear(request);
                yield noPendingSignIn();
            }
        };
    }

    @PostMapping("/enrollment")
    @Operation(summary = "Enrollment: confirm with a code of the new authenticator app",
        description = "Stores the second factor, opens the session and shows the recovery codes this once. A wrong "
            + "code counts as a failed sign-in. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Enrolled and signed in",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnrolledResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong code",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "No pending enrollment with a secret, or it expired",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "423", description = "Locked after 5 consecutive failures; lockedUntil says until when",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> confirmEnrollment(@Valid @RequestBody CodeRequest body, HttpServletRequest request,
                                        HttpServletResponse response) {
        Optional<PendingSignIn> pending = PendingSignIn
            .of(request, SignInAdministrator.NextStep.ENROLLMENT, clock.instant())
            .filter(withSecret -> withSecret.secret().isPresent());
        if (pending.isEmpty()) {
            return noPendingSignIn();
        }
        var command = new ConfirmSecondFactorEnrollment.Command(pending.get().user(),
            pending.get().secret().orElseThrow(), body.code());
        return switch (confirmEnrollment.execute(command)) {
            case ConfirmSecondFactorEnrollment.Result.Enrolled enrolled -> open(enrolled.administrator(), request,
                response, new EnrolledResponse(AdministratorResponse.of(enrolled.administrator()),
                    enrolled.recoveryCodes()));
            case ConfirmSecondFactorEnrollment.Result.InvalidCode invalid -> invalidCode();
            case ConfirmSecondFactorEnrollment.Result.Locked locked -> {
                PendingSignIn.clear(request);
                yield locked(locked.until());
            }
            case ConfirmSecondFactorEnrollment.Result.AlreadyEnrolled enrolled -> {
                PendingSignIn.clear(request);
                yield noPendingSignIn();
            }
            case ConfirmSecondFactorEnrollment.Result.NotPending notPending -> {
                PendingSignIn.clear(request);
                yield noPendingSignIn();
            }
        };
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out", description = "Invalidates the session. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    ResponseEntity<Void> signOut(HttpServletRequest request, HttpServletResponse response) {
        sessions.signOut(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in administrator")
    @ApiResponse(responseCode = "200", description = "The administrator of this session",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministratorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> me() {
        Actor actor = CurrentActor.get().orElseThrow();
        return switch (currentAdministrator.execute(actor)) {
            case GetCurrentAdministrator.Result.Found found ->
                ResponseEntity.ok(AdministratorResponse.of(found.administrator()));
            case GetCurrentAdministrator.Result.NotSignedIn notSignedIn ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED));
        };
    }

    /** Both steps passed: the pending sign-in becomes the administrator's session. */
    private <T> ResponseEntity<T> open(AdministratorView administrator, HttpServletRequest request,
                                       HttpServletResponse response, T body) {
        PendingSignIn.clear(request);
        sessions.signIn(administrator.id(), Role.ADMIN, request, response);
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<ProblemDetail> invalidCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private static ResponseEntity<ProblemDetail> invalidCode() {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid code");
    }

    private static ResponseEntity<ProblemDetail> noPendingSignIn() {
        return problem(HttpStatus.CONFLICT, "No sign-in in progress");
    }

    private static ResponseEntity<ProblemDetail> locked(Instant until) {
        var problem = ProblemDetail.forStatus(HttpStatus.LOCKED);
        problem.setTitle("Account locked");
        problem.setProperty("lockedUntil", until);
        return ResponseEntity.status(HttpStatus.LOCKED).body(problem);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /** Lengths are generous on purpose: any value that is not a valid credential gets the same 401. */
    record SignInRequest(@NotNull @Size(max = 320) String email, @NotNull @Size(max = 1024) String password) {

        @Override
        public String toString() {
            return "SignInRequest[***]";
        }
    }

    /** A 6-digit code or a recovery code; anything else is simply a wrong code. */
    record CodeRequest(@NotNull @Size(max = 64) String code) {

        @Override
        public String toString() {
            return "CodeRequest[***]";
        }
    }

    record NextStepResponse(SignInAdministrator.NextStep nextStep) {}

    /** {@code secret} and {@code otpauthUri} hold the secret of the authenticator app: shown once, never logged. */
    record EnrollmentResponse(String secret, String otpauthUri, String qrCode) {

        @Override
        public String toString() {
            return "EnrollmentResponse[***]";
        }
    }

    /** The recovery codes are shown this once. */
    record EnrolledResponse(AdministratorResponse administrator, List<String> recoveryCodes) {

        @Override
        public String toString() {
            return "EnrolledResponse[***]";
        }
    }

    record AdministratorResponse(UUID id, String email, String name) {

        static AdministratorResponse of(AdministratorView view) {
            return new AdministratorResponse(view.id().value(), view.email(), view.name());
        }
    }
}
