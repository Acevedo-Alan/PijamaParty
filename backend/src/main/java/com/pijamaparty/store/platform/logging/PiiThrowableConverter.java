package com.pijamaparty.store.platform.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * The exception of a log line (logback-spring.xml), laid out as Spring Boot's default {@code %wEx} but with every
 * message of the chain scrubbed by {@link Pii#scrubException(Throwable)} (T-E6-03, R-05), whichever library logged it.
 */
public class PiiThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy proxy) {
        if (proxy instanceof ThrowableProxy logged) {
            return super.throwableProxyToString(new ThrowableProxy(Pii.scrubException(logged.getThrowable())));
        }
        // Only events read back from another process carry no throwable; this application writes none of them.
        return super.throwableProxyToString(proxy);
    }
}
