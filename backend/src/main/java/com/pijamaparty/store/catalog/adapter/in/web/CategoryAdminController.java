package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.ActivateCategory;
import com.pijamaparty.store.catalog.application.CategoryView;
import com.pijamaparty.store.catalog.application.CreateCategory;
import com.pijamaparty.store.catalog.application.DeactivateCategory;
import com.pijamaparty.store.catalog.application.DeleteCategory;
import com.pijamaparty.store.catalog.application.ListAllCategories;
import com.pijamaparty.store.catalog.application.RenameCategory;
import com.pijamaparty.store.catalog.application.ReorderCategories;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
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
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-05: category management in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/catalog/categories")
@Tag(name = "Back office categories")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class CategoryAdminController {

    /** Bounds the request body; a shop has a handful of categories. */
    private static final int MAX_CATEGORIES_IN_ORDER = 500;

    private final ListAllCategories listAll;
    private final CreateCategory create;
    private final RenameCategory rename;
    private final ReorderCategories reorder;
    private final DeactivateCategory deactivate;
    private final ActivateCategory activate;
    private final DeleteCategory delete;

    CategoryAdminController(ListAllCategories listAll, CreateCategory create, RenameCategory rename,
                            ReorderCategories reorder, DeactivateCategory deactivate, ActivateCategory activate,
                            DeleteCategory delete) {
        this.listAll = listAll;
        this.create = create;
        this.rename = rename;
        this.reorder = reorder;
        this.deactivate = deactivate;
        this.activate = activate;
        this.delete = delete;
    }

    @GetMapping
    @Operation(summary = "Every category, active or not, by position")
    @ApiResponse(responseCode = "200", description = "The categories", content = @Content(mediaType = "application/json",
        array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class))))
    ResponseEntity<?> list() {
        return switch (listAll.execute(new ListAllCategories.Query(actor()))) {
            case ListAllCategories.Result.Listed listed -> ResponseEntity.ok(CategoryResponse.of(listed.categories()));
            case ListAllCategories.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping
    @Operation(summary = "Create a category",
        description = "The slug is generated from the name and the category goes last. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Created", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "409", description = "Another category already uses the slug of this name; see slug",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "The name has no letters or digits to build a slug from",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> create(@Valid @RequestBody NameRequest body) {
        return switch (create.execute(new CreateCategory.Command(actor(), body.name()))) {
            case CreateCategory.Result.Created created ->
                ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.of(created.category()));
            case CreateCategory.Result.SlugAlreadyUsed used -> slugAlreadyUsed(used.slug());
            case CreateCategory.Result.InvalidName invalid -> invalidName();
            case CreateCategory.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a category", description = "The slug is regenerated from the new name. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Renamed", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such category",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Another category already uses the slug of this name; see slug",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "The name has no letters or digits to build a slug from",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> rename(@PathVariable UUID id, @Valid @RequestBody NameRequest body) {
        return switch (rename.execute(new RenameCategory.Command(actor(), CategoryId.of(id), body.name()))) {
            case RenameCategory.Result.Renamed renamed -> ResponseEntity.ok(CategoryResponse.of(renamed.category()));
            case RenameCategory.Result.NotFound notFound -> notFound();
            case RenameCategory.Result.SlugAlreadyUsed used -> slugAlreadyUsed(used.slug());
            case RenameCategory.Result.InvalidName invalid -> invalidName();
            case RenameCategory.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping("/order")
    @Operation(summary = "Reorder the categories",
        description = "Takes every category id in the new order. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Every category by its new position",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class))))
    @ApiResponse(responseCode = "422", description = "The order does not name every category exactly once",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> reorder(@Valid @RequestBody OrderRequest body) {
        List<CategoryId> order = body.categoryIds().stream().map(CategoryId::of).toList();
        return switch (reorder.execute(new ReorderCategories.Command(actor(), order))) {
            case ReorderCategories.Result.Reordered reordered ->
                ResponseEntity.ok(CategoryResponse.of(reordered.categories()));
            case ReorderCategories.Result.InvalidOrder invalid ->
                problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid category order",
                    "The order must name every category exactly once.");
            case ReorderCategories.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a category", description = "Allowed with products. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Deactivated", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such category",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> deactivate(@PathVariable UUID id) {
        return switch (deactivate.execute(new DeactivateCategory.Command(actor(), CategoryId.of(id)))) {
            case DeactivateCategory.Result.Deactivated done -> ResponseEntity.ok(CategoryResponse.of(done.category()));
            case DeactivateCategory.Result.NotFound notFound -> notFound();
            case DeactivateCategory.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Reactivate a category", description = "Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Active", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such category",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> activate(@PathVariable UUID id) {
        return switch (activate.execute(new ActivateCategory.Command(actor(), CategoryId.of(id)))) {
            case ActivateCategory.Result.Activated done -> ResponseEntity.ok(CategoryResponse.of(done.category()));
            case ActivateCategory.Result.NotFound notFound -> notFound();
            case ActivateCategory.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category without products",
        description = "A category with products can only be deactivated. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such category",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The category has products: deactivate it instead",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> delete(@PathVariable UUID id) {
        return switch (delete.execute(new DeleteCategory.Command(actor(), CategoryId.of(id)))) {
            case DeleteCategory.Result.Deleted deleted -> ResponseEntity.noContent().build();
            case DeleteCategory.Result.NotFound notFound -> notFound();
            case DeleteCategory.Result.HasProducts hasProducts ->
                problem(HttpStatus.CONFLICT, "Category has products",
                    "A category with products cannot be deleted; deactivate it instead.");
            case DeleteCategory.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> slugAlreadyUsed(String slug) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Another category already uses the slug of this name.");
        problem.setTitle("Slug already used");
        problem.setProperty("slug", slug);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> invalidName() {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid category name",
            "The name needs letters or digits and at most " + CategoryName.MAX_LENGTH + " characters.");
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Category not found", null);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /** The name as typed; the use case trims it and checks the rest of the rules. */
    record NameRequest(@NotNull @Size(max = CategoryName.MAX_LENGTH) String name) {}

    record OrderRequest(@NotNull @Size(max = MAX_CATEGORIES_IN_ORDER) List<@NotNull UUID> categoryIds) {}

    record CategoryResponse(UUID id, String name, String slug, int position, boolean active) {

        static CategoryResponse of(CategoryView view) {
            return new CategoryResponse(view.id().value(), view.name(), view.slug(), view.position(), view.active());
        }

        static List<CategoryResponse> of(List<CategoryView> views) {
            return views.stream().map(CategoryResponse::of).toList();
        }
    }
}
