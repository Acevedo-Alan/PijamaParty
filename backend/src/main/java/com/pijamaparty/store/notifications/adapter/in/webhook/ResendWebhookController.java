package com.pijamaparty.store.notifications.adapter.in.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.notifications.application.RecordEmailEvent;
import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-17, UC-18 (T-E3-22): Resend's bounce and complaint notifications. The body is read raw, because the signature
 * covers its exact bytes, and checked before anything else (RNF-29.3); then, as {@code SYSTEM} (RNF-27), the event
 * is recorded once and a bounced email marked. Resend retries anything but a 2xx, so a signed notification this store
 * cannot use is answered {@code 200}. Logs carry the event type, the provider's message id and the order id only:
 * never the recipient, the subject or the body (R-05).
 */
@RestController
@Tag(name = "Notifications")
class ResendWebhookController {

    static final String PATH = "/api/mail/webhook";

    /** Resend's notifications are a few hundred bytes; the cap bounds what is read into memory (RNF-21). */
    static final int MAX_BODY_BYTES = 64 * 1024;

    /** Resend's email ids are UUIDs; anything else of a sensible shape and length is looked up too. */
    private static final Pattern MESSAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,200}");

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookController.class);

    private final ResendSignature signature;
    private final RecordEmailEvent recordEmailEvent;
    private final ObjectMapper json;

    ResendWebhookController(ResendSignature signature, RecordEmailEvent recordEmailEvent, ObjectMapper json) {
        this.signature = signature;
        this.recordEmailEvent = recordEmailEvent;
        this.json = json;
    }

    @PostMapping(PATH)
    @Operation(summary = "Resend notifies that an email bounced or was marked as spam",
        description = "Called by Resend only, signed through Svix (svix-id, svix-timestamp, svix-signature) over the "
            + "raw body. A permanent bounce or a suppressed address marks the email as bounced, and the order shows "
            + "an alert in the back office. Repeating a notification has no further effects.")
    @ApiResponse(responseCode = "200", description = "Processed, already processed, or ignored (an event type or "
        + "body this store does not use)")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or out-of-date signature",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "A body over 64 KiB",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> notifyEmailEvent(HttpServletRequest request,
                                       @RequestHeader(name = "svix-id", required = false) String id,
                                       @RequestHeader(name = "svix-timestamp", required = false) String timestamp,
                                       @RequestHeader(name = "svix-signature", required = false) String signatureHeader)
        throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", "The notification is over 64 KiB.");
        }
        if (!signature.verify(id, timestamp, signatureHeader, body)) {
            log.warn("Resend notification refused: missing, invalid or out-of-date signature{}",
                signature.isConfigured() ? "" : " (store.notifications.resend.webhook-secret is empty)");
            return problem(HttpStatus.UNAUTHORIZED, "Invalid signature", "The notification is not signed by Resend.");
        }
        Optional<Notification> notification = Notification.parse(json, body);
        if (notification.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        var command = new RecordEmailEvent.Command(MailProvider.RESEND, id, notification.get().type(),
            notification.get().messageId());
        switch (CurrentActor.callAsSystem(() -> recordEmailEvent.execute(command))) {
            case RecordEmailEvent.Result.Bounced bounced when bounced.type() == EmailType.ORDER_PAID_ADMIN ->
                log.error("The administrator's notice of order {} bounced", bounced.reference());
            case RecordEmailEvent.Result.Bounced bounced when bounced.type() == EmailType.ORDER_PAID_CUSTOMER ->
                log.warn("The {} email of order {} bounced: the back office shows an alert", bounced.type(),
                    bounced.reference());
            // UC-29: the order alert is only about the confirmation.
            case RecordEmailEvent.Result.Bounced bounced ->
                log.warn("The {} email about {} bounced", bounced.type(), bounced.reference());
            case RecordEmailEvent.Result.Recorded recorded ->
                log.info("Resend reported {} for email {}", recorded.type(), command.providerMessageId());
            case RecordEmailEvent.Result.UnknownEmail unknown ->
                log.warn("Resend reported {} for email {}, which is no sent email of this store: ignored",
                    command.type(), command.providerMessageId());
            case RecordEmailEvent.Result.AlreadyProcessed already -> {
            }
        }
        return ResponseEntity.ok().build();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /** What the store uses of a notification: its type and the email's id. Personal data is never read. */
    private record Notification(EmailEventType type, String messageId) {

        /** Empty, with a warning, for a body this store does not use. */
        static Optional<Notification> parse(ObjectMapper json, byte[] body) {
            JsonNode root;
            try {
                root = json.readTree(body);
            } catch (IOException e) {
                log.warn("Resend notification ignored: its body is not JSON");
                return Optional.empty();
            }
            if (root == null || !root.isObject()) {
                log.warn("Resend notification ignored: its body is not a JSON object");
                return Optional.empty();
            }
            String eventType = root.path("type").asText("");
            Optional<EmailEventType> type = switch (eventType) {
                case "email.bounced" -> Optional.of(bounceType(root.path("data").path("bounce").path("type")));
                case "email.suppressed" -> Optional.of(EmailEventType.SUPPRESSED);
                case "email.complained" -> Optional.of(EmailEventType.COMPLAINT);
                default -> Optional.empty();
            };
            if (type.isEmpty()) {
                return Optional.empty();
            }
            JsonNode messageId = root.path("data").path("email_id");
            if (!messageId.isTextual() || !MESSAGE_ID.matcher(messageId.asText()).matches()) {
                log.warn("Resend notification {} ignored: it has no valid email id", type.get());
                return Optional.empty();
            }
            return Optional.of(new Notification(type.get(), messageId.asText()));
        }

        /** {@code email.bounced} is a permanent rejection unless its bounce type says otherwise. */
        private static EmailEventType bounceType(JsonNode bounceType) {
            return bounceType.isMissingNode() || bounceType.isNull() || "Permanent".equalsIgnoreCase(bounceType.asText())
                ? EmailEventType.PERMANENT_BOUNCE
                : EmailEventType.TRANSIENT_BOUNCE;
        }
    }
}
