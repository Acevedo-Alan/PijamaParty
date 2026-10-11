package com.pijamaparty.store.catalog.adapter.out.image;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * RNF-21 and T-E1-14: at most {@code maxConcurrent} conversions run at a time, whatever the processor behind. Another
 * one waits up to {@code queueTimeout} for a permit and is then refused with {@link RejectedExecutionException}, which
 * the API answers with {@code 503} and {@code Retry-After}. A refused photo is never converted.
 */
final class ConcurrencyLimitedImageProcessor implements ImageProcessor {

    private final ImageProcessor delegate;
    private final Semaphore permits;
    private final Duration queueTimeout;

    ConcurrencyLimitedImageProcessor(ImageProcessor delegate, int maxConcurrent, Duration queueTimeout) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("store.images.conversions.max-concurrent must be at least 1: "
                + maxConcurrent);
        }
        if (queueTimeout.isNegative()) {
            throw new IllegalArgumentException("store.images.conversions.queue-timeout must not be negative: "
                + queueTimeout);
        }
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        // Fair, so a waiting upload is not overtaken by later ones.
        this.permits = new Semaphore(maxConcurrent, true);
        this.queueTimeout = queueTimeout;
    }

    @Override
    public Result process(byte[] content) {
        acquire();
        try {
            return delegate.process(content);
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(queueTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to convert an image", e);
        }
        if (!acquired) {
            throw new RejectedExecutionException("no image conversion slot freed up within " + queueTimeout);
        }
    }
}
