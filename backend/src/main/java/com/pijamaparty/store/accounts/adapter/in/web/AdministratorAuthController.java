package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.AdministratorView;
import com.pijamaparty.store.accounts.application.GetCurrentAdministrator;
import com.pijamaparty.store.accounts.application.SignInAdministrator;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-04: sign-in, sign-out and current user of the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Back office authentication")
class AdministratorAuthController {

    private final SignInAdministrator signIn;
    private final GetCurrentAdministrator currentAdministrator;
    private final SessionAuthentication sessions;

    AdministratorAuthController(SignInAdministrator signIn, GetCurrentAdministrator currentAdministrator,
                                SessionAuthentication sessions) {
        this.signIn = signIn;
        this.currentAdministrator = currentAdministrator;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in the administrator",
        description = "Opens a session (new session id and CSRF token). Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Signed in",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministratorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials; does not say which field failed",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "423", description = "Locked after 5 consecutive failures; lockedUntil says until when",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request,
                             HttpServletResponse response) {
        return switch (signIn.execute(new SignInAdministrator.Command(body.email(), body.password()))) {
            case SignInAdministrator.Result.SignedIn signedIn -> {
                sessions.signIn(signedIn.administrator().id(), Role.ADMIN, request, response);
                yield ResponseEntity.ok(AdministratorResponse.of(signedIn.administrator()));
            }
            case SignInAdministrator.Result.InvalidCredentials invalid -> {
                var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
                problem.setTitle("Invalid credentials");
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
            }
            case SignInAdministrator.Result.Locked locked -> {
                var problem = ProblemDetail.forStatus(HttpStatus.LOCKED);
                problem.setTitle("Account locked");
                problem.setProperty("lockedUntil", locked.until());
                yield ResponseEntity.status(HttpStatus.LOCKED).body(problem);
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

    /** Lengths are generous on purpose: any value that is not a valid credential gets the same 401. */
    record SignInRequest(@NotNull @Size(max = 320) String email, @NotNull @Size(max = 1024) String password) {

        @Override
        public String toString() {
            return "SignInRequest[***]";
        }
    }

    record AdministratorResponse(UUID id, String email, String name) {

        static AdministratorResponse of(AdministratorView view) {
            return new AdministratorResponse(view.id().value(), view.email(), view.name());
        }
    }
}
