package com.pijamaparty.store.catalog.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.application.InMemoryImageProcessor;
import com.pijamaparty.store.catalog.application.TestImages;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** RNF-21 and T-E1-14: at most 2 image conversions at a time. */
class ConcurrencyLimitedImageProcessorTest {

    private static final byte[] PHOTO = TestImages.png(1600, 1200);
    private static final long WAIT_SECONDS = 10;

    private final BlockingImageProcessor slow = new BlockingImageProcessor();
    private final ExecutorService uploads = Executors.newFixedThreadPool(4);

    @AfterEach
    void stopUploads() throws InterruptedException {
        slow.finishAll();
        uploads.shutdownNow();
        assertThat(uploads.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void givenTwoConversionsRunning_whenAThirdArrives_thenItWaitsAndRunsWhenOneEnds() throws Exception {
        var limited = new ConcurrencyLimitedImageProcessor(slow, 2, Duration.ofSeconds(WAIT_SECONDS));
        List<Future<ImageProcessor.Result>> running = startConversions(limited, 2);
        slow.awaitStarted(2);

        Future<ImageProcessor.Result> third = uploads.submit(() -> limited.process(PHOTO));
        Thread.sleep(200);
        assertThat(slow.started()).as("the third waits for a slot").isEqualTo(2);
        assertThat(slow.maxRunning()).isEqualTo(2);

        slow.finishOne();
        slow.awaitStarted(3);
        slow.finishAll();

        for (Future<ImageProcessor.Result> conversion : List.of(running.get(0), running.get(1), third)) {
            assertThat(conversion.get(WAIT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ImageProcessor.Result.Processed.class);
        }
        assertThat(slow.maxRunning()).isEqualTo(2);
    }

    @Test
    void givenTwoConversionsRunningPastTheWait_whenAThirdArrives_thenItIsRefusedWithoutConverting() throws Exception {
        var limited = new ConcurrencyLimitedImageProcessor(slow, 2, Duration.ofMillis(200));
        startConversions(limited, 2);
        slow.awaitStarted(2);

        long start = System.nanoTime();
        assertThatThrownBy(() -> limited.process(PHOTO)).isInstanceOf(RejectedExecutionException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(slow.started()).isEqualTo(2);
    }

    @Test
    void givenAConversionThatFails_whenItEnds_thenItsSlotIsFreed() {
        var failing = new ConcurrencyLimitedImageProcessor(content -> {
            throw new IllegalStateException("libvips took too long");
        }, 1, Duration.ZERO);
        assertThatThrownBy(() -> failing.process(PHOTO)).isInstanceOf(IllegalStateException.class);

        // With its only slot still taken, this one would be refused (RejectedExecutionException) at once.
        assertThatThrownBy(() -> failing.process(PHOTO)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenAnInvalidLimit_whenCreated_thenItIsRefused() {
        var delegate = new InMemoryImageProcessor();
        assertThatThrownBy(() -> new ConcurrencyLimitedImageProcessor(delegate, 0, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConcurrencyLimitedImageProcessor(delegate, 2, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Future<ImageProcessor.Result>> startConversions(ImageProcessor processor, int count) {
        List<Future<ImageProcessor.Result>> started = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            started.add(uploads.submit(() -> processor.process(PHOTO)));
        }
        return started;
    }

    /** Converts with the fake, but each conversion holds until the test lets it finish. */
    private static final class BlockingImageProcessor implements ImageProcessor {

        private final InMemoryImageProcessor fake = new InMemoryImageProcessor();
        private final Semaphore finishes = new Semaphore(0);
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger current = new AtomicInteger();
        private final AtomicInteger maxRunning = new AtomicInteger();

        @Override
        public Result process(byte[] content) {
            started.incrementAndGet();
            maxRunning.accumulateAndGet(current.incrementAndGet(), Math::max);
            try {
                if (!finishes.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the test never let the conversion finish");
                }
                return fake.process(content);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                current.decrementAndGet();
            }
        }

        /** Waits until {@code count} conversions have started in total. */
        void awaitStarted(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (started.get() < count) {
                assertThat(System.nanoTime()).as("%d conversions started", count).isLessThan(deadline);
                Thread.sleep(10);
            }
        }

        void finishOne() {
            finishes.release();
        }

        void finishAll() {
            finishes.release(1_000);
        }

        int started() {
            return started.get();
        }

        int maxRunning() {
            return maxRunning.get();
        }
    }
}
