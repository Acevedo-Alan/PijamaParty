package com.pijamaparty.store.activity.adapter.in.web;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityArea;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.application.ListActivity;
import com.pijamaparty.store.activity.application.RecordedActivity;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-37 (T-E8-01): the "Actividad" screen of the back office. Only translates between HTTP and the use case. */
@RestController
@RequestMapping("/api/admin/activity")
@Tag(name = "Back office activity")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class ActivityAdminController {

    private static final int MAX_PAGE = 10_000;

    private final ListActivity list;

    ActivityAdminController(ListActivity list) {
        this.list = list;
    }

    @GetMapping
    @Operation(summary = "The back-office changes, newest first",
        description = ListActivity.PAGE_SIZE + " per page, from page 0. Every area when `area` is absent; `from` and "
            + "`to` are store dates (America/Argentina/Buenos_Aires), both inclusive and optional.")
    @ApiResponse(responseCode = "200", description = "One page of entries", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ActivityPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown area, a date that is not yyyy-MM-dd, `from` after `to`, "
        + "or a page out of range",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> list(@RequestParam Optional<ActivityArea> area,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> to,
                           @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (list.execute(new ListActivity.Query(actor(), area, from, to, page))) {
            case ListActivity.Result.Listed listed -> ResponseEntity.ok(new ActivityPageResponse(
                listed.entries().stream().map(ActivityEntryResponse::of).toList(), listed.hasMore()));
            case ListActivity.Result.InvalidPeriod invalid -> invalidPeriod();
            case ListActivity.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidPeriod() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "`from` must not be after `to`.");
        problem.setTitle("Invalid period");
        return ResponseEntity.badRequest().body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    record ActivityPageResponse(List<ActivityEntryResponse> items, boolean hasMore) {}

    /** @param changes the changed fields in the order the use case recorded them; empty when the action says it all */
    record ActivityEntryResponse(UUID id, Instant occurredAt, ActivityArea area, ActivityAction action,
                                 ActivitySubjectResponse subject, List<ActivityChangeResponse> changes) {

        static ActivityEntryResponse of(RecordedActivity activity) {
            var entry = activity.entry();
            return new ActivityEntryResponse(activity.id(), activity.occurredAt(), entry.action().area(),
                entry.action(), new ActivitySubjectResponse(entry.subject().type(),
                    entry.subject().id().orElse(null), entry.subject().label().orElse(null)),
                entry.changes().stream().map(ActivityChangeResponse::of).toList());
        }
    }

    /**
     * @param id null for a subject without an id of its own (the shipping table, the store settings)
     * @param label null when the id says enough
     */
    record ActivitySubjectResponse(SubjectType type, @Schema(nullable = true) UUID id,
                                   @Schema(nullable = true) String label) {}

    /** @param before null when the field had no value; {@code after} likewise */
    record ActivityChangeResponse(String field, @Schema(nullable = true) String before,
                                  @Schema(nullable = true) String after) {

        static ActivityChangeResponse of(FieldChange change) {
            return new ActivityChangeResponse(change.field(), change.before().orElse(null),
                change.after().orElse(null));
        }
    }
}
