package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.DeleteSizeGuide;
import com.pijamaparty.store.catalog.application.EditSizeGuide;
import com.pijamaparty.store.catalog.application.GetSizeGuide;
import com.pijamaparty.store.catalog.application.SizeGuideView;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RNF-31.5: the size guide of a category in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/catalog/categories/{id}/size-guide")
@Tag(name = "Back office size guides")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "No such category",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class SizeGuideAdminController {

    /** Bound the request body only; the use case checks the guide's own limits and answers 422. */
    private static final int MAX_LIST = 100;
    private static final int MAX_TEXT = 200;
    private static final int MAX_NOTE = 5_000;

    private final GetSizeGuide get;
    private final EditSizeGuide edit;
    private final DeleteSizeGuide delete;

    SizeGuideAdminController(GetSizeGuide get, EditSizeGuide edit, DeleteSizeGuide delete) {
        this.get = get;
        this.edit = edit;
        this.delete = delete;
    }

    @GetMapping
    @Operation(summary = "The category's name and its size guide, null without one")
    @ApiResponse(responseCode = "200", description = "The category and its guide",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoryGuideResponse.class)))
    ResponseEntity<?> get(@PathVariable UUID id) {
        return switch (get.execute(new GetSizeGuide.Query(actor(), CategoryId.of(id)))) {
            case GetSizeGuide.Result.Found found -> ResponseEntity.ok(new CategoryGuideResponse(
                new SizeGuideCategoryResponse(found.category().id().value(), found.category().name()),
                found.guide().map(AdminSizeGuideResponse::of).orElse(null)));
            case GetSizeGuide.Result.NotFound notFound -> notFound();
            case GetSizeGuide.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping
    @Operation(summary = "Create or replace the category's size guide",
        description = "Replaces the whole guide. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The saved guide",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminSizeGuideResponse.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_SIZE_GUIDE with the invalid fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> edit(@PathVariable UUID id, @Valid @RequestBody SizeGuideRequest body) {
        return switch (edit.execute(new EditSizeGuide.Command(actor(), CategoryId.of(id), body.toForm()))) {
            case EditSizeGuide.Result.Saved saved -> ResponseEntity.ok(AdminSizeGuideResponse.of(saved.guide()));
            case EditSizeGuide.Result.NotFound notFound -> notFound();
            case EditSizeGuide.Result.InvalidGuide invalid -> invalidGuide(invalid.fields());
            case EditSizeGuide.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping
    @Operation(summary = "Delete the category's size guide",
        description = "Also answers 204 when the category had none. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    ResponseEntity<?> delete(@PathVariable UUID id) {
        return switch (delete.execute(new DeleteSizeGuide.Command(actor(), CategoryId.of(id)))) {
            case DeleteSizeGuide.Result.Deleted deleted -> ResponseEntity.noContent().build();
            case DeleteSizeGuide.Result.NotFound notFound -> notFound();
            case DeleteSizeGuide.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidGuide(Set<SizeGuideForm.Field> fields) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Columns: 1 to " + SizeGuide.MAX_COLUMNS + " different names of at most " + SizeGuide.MAX_COLUMN_NAME
                + " characters. Rows: 1 to " + SizeGuide.MAX_ROWS + " different sizes, each with one value of at most "
                + SizeGuide.MAX_VALUE + " characters per column. Note: optional, at most " + SizeGuide.MAX_NOTE
                + " characters.");
        problem.setTitle("Invalid size guide");
        problem.setProperty("problem", "INVALID_SIZE_GUIDE");
        problem.setProperty("fields", fields.stream()
            .sorted(Comparator.naturalOrder())
            .map(SizeGuideAdminController::fieldName)
            .toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static String fieldName(SizeGuideForm.Field field) {
        return switch (field) {
            case COLUMNS -> "columns";
            case ROWS -> "rows";
            case NOTE -> "note";
        };
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Category not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    /** The guide as typed; the use case trims it and checks its rules. */
    record SizeGuideRequest(@NotNull @Size(max = MAX_LIST) List<@NotNull @Size(max = MAX_TEXT) String> columns,
                            @NotNull @Size(max = MAX_LIST) List<@NotNull @Valid RowRequest> rows,
                            @Size(max = MAX_NOTE) String note) {

        SizeGuideForm toForm() {
            return new SizeGuideForm(columns,
                rows.stream().map(row -> new SizeGuideForm.RowForm(row.size(), row.values())).toList(), note);
        }
    }

    record RowRequest(@NotNull @Size(max = MAX_TEXT) String size,
                      @NotNull @Size(max = MAX_LIST) List<@NotNull @Size(max = MAX_TEXT) String> values) {}

    record CategoryGuideResponse(SizeGuideCategoryResponse category,
                                 @Schema(nullable = true) AdminSizeGuideResponse guide) {}

    record SizeGuideCategoryResponse(UUID id, String name) {}

    record AdminSizeGuideResponse(List<String> columns, List<SizeGuideResponse.SizeGuideRowResponse> rows,
                                  @Schema(nullable = true) String note, Instant updatedAt) {

        static AdminSizeGuideResponse of(SizeGuideView guide) {
            return new AdminSizeGuideResponse(guide.columns(), SizeGuideResponse.SizeGuideRowResponse.of(guide.rows()),
                guide.note().orElse(null), guide.updatedAt());
        }
    }
}
