package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.AddAddress;
import com.pijamaparty.store.accounts.application.AddressView;
import com.pijamaparty.store.accounts.application.DeleteAddress;
import com.pijamaparty.store.accounts.application.EditAddress;
import com.pijamaparty.store.accounts.application.ListAddresses;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.accounts.domain.Province;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
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

/** UC-20 (T-E4-05): the signed-in customer's address book. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/account/addresses")
@Tag(name = "Customer account")
class AddressBookController {

    private static final String PATH = "/api/account/addresses";
    private static final int MAX_TEXT = 300;

    private final ListAddresses list;
    private final AddAddress add;
    private final EditAddress edit;
    private final DeleteAddress delete;

    AddressBookController(ListAddresses list, AddAddress add, EditAddress edit, DeleteAddress delete) {
        this.list = list;
        this.add = add;
        this.edit = edit;
        this.delete = delete;
    }

    @GetMapping
    @Operation(summary = "The customer's addresses", description = "The primary first, then from the oldest.")
    @ApiResponse(responseCode = "200", description = "The addresses; empty when there are none",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = SavedAddressResponse.class))))
    @ApiResponse(responseCode = "401", description = "Not signed in as a customer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> listAddresses() {
        return switch (list.execute(actor())) {
            case ListAddresses.Result.Listed listed ->
                ResponseEntity.ok(listed.addresses().stream().map(SavedAddressResponse::of).toList());
            case ListAddresses.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @PostMapping
    @Operation(summary = "Add an address",
        description = "The first address is the primary whatever primary says; primary true makes the new one the "
            + "primary instead of the previous one. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "The new address",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = SavedAddressResponse.class)))
    @ApiResponse(responseCode = "422", description = "ADDRESS_BOOK_FULL (already "
        + AddressBook.MAX_ADDRESSES + " addresses), or INVALID_ADDRESS with the invalid fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> addAddress(@Valid @RequestBody SavedAddressRequest body) {
        return switch (add.execute(new AddAddress.Command(actor(), body.toForm(), body.isPrimary()))) {
            case AddAddress.Result.Added added -> ResponseEntity
                .created(URI.create(PATH + "/" + added.address().id()))
                .body(SavedAddressResponse.of(added.address()));
            case AddAddress.Result.InvalidAddress invalid -> invalidAddress(invalid.fields());
            case AddAddress.Result.AddressBookFull full -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Address book full", "ADDRESS_BOOK_FULL");
            case AddAddress.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an address",
        description = "Replaces every field. primary true makes it the primary; false or absent leaves it as it "
            + "was. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The address",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = SavedAddressResponse.class)))
    @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_ADDRESS with the invalid fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> editAddress(@PathVariable UUID id, @Valid @RequestBody SavedAddressRequest body) {
        var command = new EditAddress.Command(actor(), AddressId.of(id), body.toForm(), body.isPrimary());
        return switch (edit.execute(command)) {
            case EditAddress.Result.Edited edited -> ResponseEntity.ok(SavedAddressResponse.of(edited.address()));
            case EditAddress.Result.InvalidAddress invalid -> invalidAddress(invalid.fields());
            case EditAddress.Result.NotFound notFound -> notFound();
            case EditAddress.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an address",
        description = "When it was the primary, the oldest remaining address becomes primary. Orders keep their own "
            + "copy of the address. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> deleteAddress(@PathVariable UUID id) {
        return switch (delete.execute(new DeleteAddress.Command(actor(), AddressId.of(id)))) {
            case DeleteAddress.Result.Deleted deleted -> ResponseEntity.noContent().build();
            case DeleteAddress.Result.NotFound notFound -> notFound();
            case DeleteAddress.Result.NotSignedIn notSignedIn -> notSignedIn();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidAddress(Set<AddressForm.Field> fields) {
        var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Invalid address");
        problem.setProperty("problem", "INVALID_ADDRESS");
        problem.setProperty("fields", fields.stream()
            .sorted(Comparator.naturalOrder())
            .map(AddressBookController::fieldName)
            .toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static String fieldName(AddressForm.Field field) {
        return switch (field) {
            case STREET -> "street";
            case NUMBER -> "number";
            case FLOOR -> "floor";
            case APARTMENT -> "apartment";
            case CITY -> "city";
            case POSTAL_CODE -> "postalCode";
        };
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Address not found", "ADDRESS_NOT_FOUND");
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

    /** Lengths are generous on purpose: the domain gives the precise rule of each field. */
    record SavedAddressRequest(@NotNull @Size(max = MAX_TEXT) String street, @NotNull @Size(max = MAX_TEXT) String number,
                          @Size(max = MAX_TEXT) String floor, @Size(max = MAX_TEXT) String apartment,
                          @NotNull @Size(max = MAX_TEXT) String city, @NotNull Province province,
                          @NotNull @Size(max = MAX_TEXT) String postalCode, Boolean primary) {

        AddressForm toForm() {
            return new AddressForm(street, number, floor, apartment, city, province, postalCode);
        }

        boolean isPrimary() {
            return Boolean.TRUE.equals(primary);
        }

        @Override
        public String toString() {
            return "SavedAddressRequest[***]";
        }
    }

    record SavedAddressResponse(UUID id, String street, String number, @Schema(nullable = true) String floor,
                           @Schema(nullable = true) String apartment, String city, Province province,
                           String postalCode, boolean primary) {

        static SavedAddressResponse of(AddressView view) {
            return new SavedAddressResponse(view.id().value(), view.street(), view.number(), view.floor().orElse(null),
                view.apartment().orElse(null), view.city(), view.province(), view.postalCode(), view.primary());
        }

        @Override
        public String toString() {
            return "SavedAddressResponse[id=" + id + ", ***]";
        }
    }
}
