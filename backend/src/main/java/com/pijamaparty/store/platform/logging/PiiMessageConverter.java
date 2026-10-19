package com.pijamaparty.store.platform.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * {@code %m} of the log layout (logback-spring.xml): every message, the store's and every library's, goes through
 * {@link Pii#scrub(String)} before it is written (T-E6-03, R-05). A safety net: call sites still mask what they log.
 */
public class PiiMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return Pii.scrub(super.convert(event));
    }
}
