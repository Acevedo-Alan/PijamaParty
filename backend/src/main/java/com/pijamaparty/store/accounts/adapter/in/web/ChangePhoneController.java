package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.ChangePhone;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-40 (T-E4-18): the signed-in customer's phone, from "Datos y acceso". Only translates HTTP and the use case. */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Customer account")
class ChangePhoneController {

    private final ChangePhone changePhone;

    ChangePhoneController(ChangePhone changePhone) {
        this.changePhone = changePhone;
    }

    @PutMapping("/phone")
    @Operation(summary = "Add or change the customer's phone",
        description = "Spaces, +, - and parentheses are dropped, as in the checkout; 8 to 15 digits remain and are "
            + "stored. The next checkout prefills it. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The stored phone, digits only",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = PhoneResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_PHONE: not 8 to 15 digits",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> changePhone(@Valid @RequestBody PhoneRequest body) {
        var command = new ChangePhone.Command(CurrentActor.get().orElseThrow(), body.phone());
        return switch (changePhone.execute(command)) {
            case ChangePhone.Result.Changed changed -> ResponseEntity.ok(new PhoneResponse(changed.phone()));
            case ChangePhone.Result.InvalidPhone invalid -> {
                var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
                problem.setTitle("Invalid phone");
                problem.setProperty("problem", "INVALID_PHONE");
                yield ResponseEntity.unprocessableEntity().body(problem);
            }
            case ChangePhone.Result.NotSignedIn notSignedIn ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED));
        };
    }

    /** Generous on purpose: the use case gives the precise reason. Personal data: never logged. */
    record PhoneRequest(@NotNull @Size(max = 64) String phone) {

        @Override
        public String toString() {
            return "PhoneRequest[***]";
        }
    }

    record PhoneResponse(String phone) {

        @Override
        public String toString() {
            return "PhoneResponse[***]";
        }
    }
}
