package com.pijamaparty.store.backoffice.adapter.in.web;

import com.pijamaparty.store.backoffice.application.GetSetupGuide;
import com.pijamaparty.store.backoffice.application.OmitSetupItem;
import com.pijamaparty.store.backoffice.application.ShowSetupItem;
import com.pijamaparty.store.backoffice.domain.SetupFacts;
import com.pijamaparty.store.backoffice.domain.SetupGuide;
import com.pijamaparty.store.backoffice.domain.SetupItem;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-46: the store setup guide of the summary. Only translates between HTTP and the use cases. */
@RestController
@Tag(name = "Back office summary")
class SetupGuideAdminController {

    private static final String GUIDE = "/api/admin/setup-guide";
    private static final String OMITTED = GUIDE + "/omitted/{item}";

    private final GetSetupGuide getSetupGuide;
    private final OmitSetupItem omitSetupItem;
    private final ShowSetupItem showSetupItem;

    SetupGuideAdminController(GetSetupGuide getSetupGuide, OmitSetupItem omitSetupItem, ShowSetupItem showSetupItem) {
        this.getSetupGuide = getSetupGuide;
        this.omitSetupItem = omitSetupItem;
        this.showSetupItem = showSetupItem;
    }

    @GetMapping(GUIDE)
    @Operation(summary = "The store setup guide: each item in order, done, pending or omitted")
    @ApiResponse(responseCode = "200", description = "The guide", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = SetupGuideResponse.class)))
    @ApiResponse(responseCode = "403", description = "Not the administrator",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> get() {
        return switch (getSetupGuide.execute(new GetSetupGuide.Query(CurrentActor.get().orElseThrow()))) {
            case GetSetupGuide.Result.Shown shown -> ResponseEntity.ok(SetupGuideResponse.from(shown.guide()));
            case GetSetupGuide.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping(OMITTED)
    @Operation(summary = "Omits an item the store may do without (INSTAGRAM, HOME_BANNER); idempotent")
    @ApiResponse(responseCode = "204", description = "Omitted, now or before")
    @ApiResponse(responseCode = "400", description = "Not an item of the guide",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the administrator",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "An item a working store needs, which stays until done",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> omit(@PathVariable SetupItem item) {
        return switch (omitSetupItem.execute(new OmitSetupItem.Command(CurrentActor.get().orElseThrow(), item))) {
            case OmitSetupItem.Result.Omitted omitted -> ResponseEntity.noContent().build();
            case OmitSetupItem.Result.NotOmittable notOmittable -> notOmittable();
            case OmitSetupItem.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping(OMITTED)
    @Operation(summary = "Shows an omitted item again; idempotent")
    @ApiResponse(responseCode = "204", description = "Shown, or it was not omitted")
    @ApiResponse(responseCode = "400", description = "Not an item of the guide",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the administrator",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> show(@PathVariable SetupItem item) {
        return switch (showSetupItem.execute(new ShowSetupItem.Command(CurrentActor.get().orElseThrow(), item))) {
            case ShowSetupItem.Result.Shown shown -> ResponseEntity.noContent().build();
            case ShowSetupItem.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static ResponseEntity<ProblemDetail> notOmittable() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Only Instagram and the home banner can be omitted; the other items stay until done.");
        problem.setTitle("Item not omittable");
        problem.setProperty("problem", "SETUP_ITEM_NOT_OMITTABLE");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    /** @param items every item of the guide, in the order the summary shows them */
    record SetupGuideResponse(List<SetupItemResponse> items) {

        static SetupGuideResponse from(SetupGuide guide) {
            return new SetupGuideResponse(guide.entries().stream().map(SetupItemResponse::from).toList());
        }
    }

    /**
     * @param total how many categories or products the item is about while pending (it names the first ones); 0 for
     *              the other items
     * @param names the first of them, at most 5
     */
    record SetupItemResponse(SetupItem item, SetupGuide.State state, long total, List<SetupNameResponse> names) {

        static SetupItemResponse from(SetupGuide.Entry entry) {
            return new SetupItemResponse(entry.item(), entry.state(), entry.named().total(),
                entry.named().first().stream().map(SetupNameResponse::from).toList());
        }
    }

    record SetupNameResponse(UUID id, String name) {

        static SetupNameResponse from(SetupFacts.Thing thing) {
            return new SetupNameResponse(thing.id(), thing.name());
        }
    }
}
