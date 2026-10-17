package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.CustomerView;
import com.pijamaparty.store.accounts.application.GetCurrentCustomer;
import com.pijamaparty.store.accounts.application.RegisterCustomer;
import com.pijamaparty.store.accounts.application.RequestPasswordReset;
import com.pijamaparty.store.accounts.application.ResetPassword;
import com.pijamaparty.store.accounts.application.SignInCustomer;
import com.pijamaparty.store.accounts.application.VerifyCustomerEmail;
import com.pijamaparty.store.accounts.domain.PasswordRules;
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
import java.time.Instant;
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
 * UC-19 (T-E4-02, T-E4-03): registration, email verification, sign-in, sign-out, current customer and password
 * recovery of the storefront. Only translates between HTTP, the session and the use cases.
 */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Customer account")
class CustomerAccountController {

    private final RegisterCustomer register;
    private final VerifyCustomerEmail verifyEmail;
    private final SignInCustomer signIn;
    private final GetCurrentCustomer currentCustomer;
    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;
    private final SessionAuthentication sessions;

    CustomerAccountController(RegisterCustomer register, VerifyCustomerEmail verifyEmail, SignInCustomer signIn,
                              GetCurrentCustomer currentCustomer, RequestPasswordReset requestPasswordReset,
                              ResetPassword resetPassword, SessionAuthentication sessions) {
        this.register = register;
        this.verifyEmail = verifyEmail;
        this.signIn = signIn;
        this.currentCustomer = currentCustomer;
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @Operation(summary = "Register with email, password and name",
        description = "Creates the account unverified and sends the email with the verification link, which lasts "
            + "24 hours from the sending. Does not sign in. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Registered; the verification email is on its way")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED; says nothing else about that account",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_EMAIL, INVALID_NAME (1 to 100 characters), "
        + "PASSWORD_TOO_SHORT (under 8 characters) or PASSWORD_TOO_LONG (over 72 bytes)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> register(@Valid @RequestBody RegisterRequest body) {
        var command = new RegisterCustomer.Command(body.email(), body.password(), body.name());
        return switch (register.execute(command)) {
            case RegisterCustomer.Result.Registered registered -> ResponseEntity.status(HttpStatus.CREATED).build();
            case RegisterCustomer.Result.EmailAlreadyRegistered taken -> problem(HttpStatus.CONFLICT,
                "Email already registered", "EMAIL_ALREADY_REGISTERED");
            case RegisterCustomer.Result.InvalidEmail invalid -> invalid("INVALID_EMAIL");
            case RegisterCustomer.Result.InvalidName invalid -> invalid("INVALID_NAME");
            case RegisterCustomer.Result.InvalidPassword invalid -> invalid(passwordProblem(invalid.violation()));
        };
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify the email with the token of its link",
        description = "Uses the token, once. Does not sign in: the customer signs in afterwards. Requires the CSRF "
            + "token.")
    @ApiResponse(responseCode = "204", description = "Verified")
    @ApiResponse(responseCode = "410", description = "INVALID_LINK: unknown, used or expired; does not say which",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        return switch (verifyEmail.execute(new VerifyCustomerEmail.Command(body.token()))) {
            case VerifyCustomerEmail.Result.Verified verified -> ResponseEntity.noContent().build();
            case VerifyCustomerEmail.Result.InvalidLink invalid ->
                problem(HttpStatus.GONE, "Invalid or expired link", "INVALID_LINK");
        };
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password",
        description = "Opens the customer's session (new session id and CSRF token). Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Signed in",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials; does not say which field failed",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED: right password, email not verified yet; a new "
        + "verification email is on its way, unless one is waiting to be sent",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "423", description = "Locked after 5 consecutive failures; lockedUntil says until when",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> signIn(@Valid @RequestBody CustomerSignInRequest body, HttpServletRequest request,
                             HttpServletResponse response) {
        return switch (signIn.execute(new SignInCustomer.Command(body.email(), body.password()))) {
            case SignInCustomer.Result.SignedIn signedIn -> {
                sessions.signIn(signedIn.customer().id(), Role.CUSTOMER, request, response);
                yield ResponseEntity.ok(CustomerResponse.of(signedIn.customer()));
            }
            case SignInCustomer.Result.InvalidCredentials invalid -> {
                var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
                problem.setTitle("Invalid credentials");
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
            }
            case SignInCustomer.Result.EmailNotVerified notVerified ->
                problem(HttpStatus.FORBIDDEN, "Email not verified", "EMAIL_NOT_VERIFIED");
            case SignInCustomer.Result.Locked locked -> locked(locked.until());
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
    @Operation(summary = "The signed-in customer")
    @ApiResponse(responseCode = "200", description = "The customer of this session",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> me() {
        Actor actor = CurrentActor.get().orElseThrow();
        return switch (currentCustomer.execute(actor)) {
            case GetCurrentCustomer.Result.Found found -> ResponseEntity.ok(CustomerResponse.of(found.customer()));
            case GetCurrentCustomer.Result.NotSignedIn notSignedIn ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED));
        };
    }

    @PostMapping("/password-recovery")
    @Operation(summary = "Ask for a password reset link",
        description = "A customer with that email gets the reset email, whose link lasts 30 minutes from the "
            + "sending. The answer is the same whether the email belongs to an account or not. Requires the CSRF "
            + "token.")
    @ApiResponse(responseCode = "204", description = "Always, whatever the email")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordRecoveryRequest body) {
        return switch (requestPasswordReset.execute(new RequestPasswordReset.Command(body.email()))) {
            case RequestPasswordReset.Result.Requested requested -> ResponseEntity.noContent().build();
        };
    }

    @PostMapping("/password-reset")
    @Operation(summary = "Set a new password with the token of a reset link",
        description = "Uses the token, once, and makes the older reset links stop working. Clears the lock, verifies "
            + "the email and ends every session of the account. Does not sign in. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "The password was replaced")
    @ApiResponse(responseCode = "410", description = "INVALID_LINK: unknown, used or expired; does not say which",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "PASSWORD_TOO_SHORT (under 8 characters) or PASSWORD_TOO_LONG "
        + "(over 72 bytes); the link stays unused",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordResetRequest body) {
        return switch (resetPassword.execute(new ResetPassword.Command(body.token(), body.password()))) {
            case ResetPassword.Result.Reset reset -> ResponseEntity.noContent().build();
            case ResetPassword.Result.InvalidLink invalid ->
                problem(HttpStatus.GONE, "Invalid or expired link", "INVALID_LINK");
            case ResetPassword.Result.InvalidPassword invalid -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid password", passwordProblem(invalid.violation()));
        };
    }

    private static String passwordProblem(PasswordRules.Violation violation) {
        return violation == PasswordRules.Violation.TOO_SHORT ? "PASSWORD_TOO_SHORT" : "PASSWORD_TOO_LONG";
    }

    private static ResponseEntity<ProblemDetail> invalid(String code) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid registration", code);
    }

    private static ResponseEntity<ProblemDetail> locked(Instant until) {
        var problem = ProblemDetail.forStatus(HttpStatus.LOCKED);
        problem.setTitle("Account locked");
        problem.setProperty("lockedUntil", until);
        return ResponseEntity.status(HttpStatus.LOCKED).body(problem);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String code) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("problem", code);
        return ResponseEntity.status(status).body(problem);
    }

    /** Lengths are generous on purpose: the use case gives the precise reason for each field. */
    record RegisterRequest(@NotNull @Size(max = 320) String email, @NotNull @Size(max = 1024) String password,
                           @NotNull @Size(max = 400) String name) {

        @Override
        public String toString() {
            return "RegisterRequest[***]";
        }
    }

    /** Any value that is not a token of a link is simply an invalid link. */
    record VerifyEmailRequest(@NotNull @Size(max = 256) String token) {

        @Override
        public String toString() {
            return "VerifyEmailRequest[***]";
        }
    }

    /** As the administrator's: any value that is not a valid credential gets the same 401. */
    record CustomerSignInRequest(@NotNull @Size(max = 320) String email,
                                 @NotNull @Size(max = 1024) String password) {

        @Override
        public String toString() {
            return "CustomerSignInRequest[***]";
        }
    }

    /** Any value gets the same answer, so a malformed email is not refused either. */
    record PasswordRecoveryRequest(@NotNull @Size(max = 320) String email) {

        @Override
        public String toString() {
            return "PasswordRecoveryRequest[***]";
        }
    }

    /** As the verification's: any value that is not a token of a link is simply an invalid link. */
    record PasswordResetRequest(@NotNull @Size(max = 256) String token, @NotNull @Size(max = 1024) String password) {

        @Override
        public String toString() {
            return "PasswordResetRequest[***]";
        }
    }

    /** {@code phone}: the account's, left by its last purchase (UC-21), or null when it has none. */
    record CustomerResponse(UUID id, String email, String name,
                            @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String phone) {

        static CustomerResponse of(CustomerView view) {
            return new CustomerResponse(view.id().value(), view.email(), view.name(), view.phone().orElse(null));
        }

        @Override
        public String toString() {
            return "CustomerResponse[***]";
        }
    }
}
