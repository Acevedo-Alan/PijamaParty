package com.pijamaparty.store.notifications.adapter.in.scheduler;

import com.pijamaparty.store.notifications.application.SendNextEmail;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RNF-23, UC-17: every minute ({@code store.notifications.outbox.cron}) the emails due are sent, one at a time, at
 * most {@link #MAX_PER_RUN} per run. Logs carry the type and the order id of a failed email, never its recipient or
 * content (R-05).
 */
@Component
class EmailOutboxDispatcher {

    /** Far above what a minute brings at fewer than 20 orders a day; the rest wait for the next run. */
    static final int MAX_PER_RUN = 50;

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);

    private final SendNextEmail sendNextEmail;

    EmailOutboxDispatcher(SendNextEmail sendNextEmail) {
        this.sendNextEmail = sendNextEmail;
    }

    @Scheduled(cron = "${store.notifications.outbox.cron}", zone = TimeConfiguration.STORE_ZONE_ID)
    void run() {
        int sent = 0;
        int retrying = 0;
        int failed = 0;
        for (int taken = 0; taken < MAX_PER_RUN; taken++) {
            SendNextEmail.Result result = CurrentActor.callAsSystem(sendNextEmail::execute);
            if (result instanceof SendNextEmail.Result.NoneDue) {
                break;
            }
            switch (result) {
                case SendNextEmail.Result.Sent done -> sent++;
                case SendNextEmail.Result.Retrying again -> retrying++;
                case SendNextEmail.Result.Failed given -> {
                    failed++;
                    log.error("Email {} about {} failed and will not be sent: contact the buyer", given.type(),
                        given.reference());
                }
                case SendNextEmail.Result.NoneDue none -> {
                }
            }
        }
        if (sent + retrying + failed > 0) {
            log.info("Email outbox sent {}, will retry {}, failed {}", sent, retrying, failed);
        }
    }
}
