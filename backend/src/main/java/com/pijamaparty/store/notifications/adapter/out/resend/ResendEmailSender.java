package com.pijamaparty.store.notifications.adapter.out.resend;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.notifications.application.EmailSender;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * RNF-23: the emails go out through Resend, {@code POST /emails}. Anti-corruption layer: Resend's JSON never leaves
 * this class. Its own HTTP client with the RNF-21 timeouts, no redirects and no retries (the outbox retries). Logs
 * carry the HTTP status only: never the recipient, the content or Resend's answer (R-05).
 */
class ResendEmailSender implements EmailSender {

    static final String EMAILS_PATH = "/emails";

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient client;
    private final boolean configured;
    private final String from;
    private final Optional<String> replyTo;

    /**
     * @param apiKey  empty: every email is {@code Unavailable}, without calling
     * @param from    the sender, e.g. {@code Pijama Party <pedidos@notificaciones.example>}; empty, as without a key
     * @param replyTo where the buyer's answers go; empty: none
     */
    ResendEmailSender(ResendProperties properties, String apiKey, String from, String replyTo) {
        this.configured = !apiKey.isBlank() && !from.isBlank();
        this.from = from.strip();
        this.replyTo = Optional.of(replyTo.strip()).filter(address -> !address.isEmpty());
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.baseUrl().toString())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .build();
    }

    @Override
    public Outcome send(EmailMessage message) {
        if (!configured) {
            log.warn("Resend has no API key or sender (store.notifications.resend.api-key, store.notifications.from):"
                + " the email waits");
            return new Outcome.Unavailable();
        }
        SentEmail response;
        try {
            response = client.post()
                .uri(EMAILS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new Email(from, List.of(message.to()), replyTo.orElse(null), message.subject(), message.html(),
                    message.text()))
                .retrieve()
                .body(SentEmail.class);
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            log.warn("Resend refused an email: HTTP {}", status);
            // 400 and 422: this email is invalid (recipient, content); anything else may pass later.
            return status == 400 || status == 422 ? new Outcome.Rejected() : new Outcome.Unavailable();
        } catch (RestClientException unreachable) {
            log.warn("Resend did not take an email: {}", unreachable.getClass().getSimpleName());
            return new Outcome.Unavailable();
        }
        if (response == null || response.id() == null || response.id().isBlank() || response.id().length() > 200) {
            log.warn("Resend answered an email without a valid id");
            return new Outcome.Unavailable();
        }
        return new Outcome.Sent(response.id());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Email(String from, List<String> to, @JsonProperty("reply_to") String replyTo, String subject, String html,
                 String text) {

        @Override
        public String toString() {
            return "Email[***]";
        }
    }

    record SentEmail(String id) {}
}
