package com.pijamaparty.store.catalog.adapter.out.image;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.domain.ImageDimensions;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.ProductImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts photos with the libvips command line (UC-06, RNF-29.4), installed in the API image. Each step is a
 * separate process with a timeout, so a decoder crash or a decompression bomb never reaches the JVM or its heap.
 * {@code VIPS_BLOCK_UNTRUSTED} keeps libvips to its fuzzed loaders, and {@code vipsheader} reads the dimensions without
 * decoding the pixels.
 */
@Component
class VipsImageProcessor implements ImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(VipsImageProcessor.class);

    private static final int QUALITY = 80;
    private static final int PLACEHOLDER_QUALITY = 40;
    private static final int PLACEHOLDER_BOX = 10;
    /** Above any height the limits allow, so a rendition is bounded by its width only. */
    private static final int UNBOUNDED_HEIGHT = 100_000;

    private final String vips;
    private final String vipsheader;
    private final Duration timeout;

    /** {@code binDir} empty: the commands are found on the PATH. */
    VipsImageProcessor(@Value("${store.images.vips.bin-dir:}") String binDir,
                       @Value("${store.images.vips.timeout:30s}") Duration timeout) {
        this.vips = command(binDir, "vips");
        this.vipsheader = command(binDir, "vipsheader");
        this.timeout = timeout;
    }

    private static String command(String binDir, String name) {
        return binDir.isBlank() ? name : Path.of(binDir).resolve(name).toString();
    }

    @Override
    public Result process(byte[] content) {
        Path work = null;
        try {
            work = Files.createTempDirectory("store-image-");
            Path input = work.resolve("input");
            Files.write(input, content);
            Optional<ImageDimensions> dimensions = dimensionsOf(work, input);
            if (dimensions.isEmpty()) {
                return new Result.Unreadable();
            }
            if (!dimensions.get().withinLimits()) {
                return new Result.TooLarge();
            }
            List<Rendition> renditions = new ArrayList<>();
            for (int width : ProductImage.WIDTHS) {
                Path output = work.resolve(width + ".webp");
                if (!thumbnail(work, input, output, QUALITY, width, "--height", String.valueOf(UNBOUNDED_HEIGHT))) {
                    return new Result.Unreadable();
                }
                renditions.add(new Rendition(width, Files.readAllBytes(output)));
            }
            // Without --height the box is square: the placeholder fits in 10 x 10 whatever the photo's shape.
            Path placeholder = work.resolve("placeholder.webp");
            if (!thumbnail(work, input, placeholder, PLACEHOLDER_QUALITY, PLACEHOLDER_BOX)) {
                return new Result.Unreadable();
            }
            return new Result.Processed(renditions, ImagePlaceholder.ofWebp(Files.readAllBytes(placeholder)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(work);
        }
    }

    private Optional<ImageDimensions> dimensionsOf(Path work, Path input) throws IOException {
        Optional<Integer> width = run(work, vipsheader, "-f", "width", input.toString()).flatMap(this::integer);
        Optional<Integer> height = run(work, vipsheader, "-f", "height", input.toString()).flatMap(this::integer);
        if (width.isEmpty() || height.isEmpty() || width.get() < 1 || height.get() < 1) {
            return Optional.empty();
        }
        return Optional.of(new ImageDimensions(width.get(), height.get()));
    }

    private Optional<Integer> integer(String output) {
        try {
            return Optional.of(Integer.parseInt(output.strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Never enlarges ({@code --size down}), applies the orientation (the default), converts to sRGB and keeps no
     * metadata. An animated image keeps its first frame (the default).
     */
    private boolean thumbnail(Path work, Path input, Path output, int quality, int width, String... options)
        throws IOException {
        List<String> command = new ArrayList<>(List.of(vips, "thumbnail", input.toString(),
            output + "[Q=" + quality + ",keep=none]", String.valueOf(width), "--size", "down",
            "--export-profile", "srgb"));
        command.addAll(List.of(options));
        return run(work, command.toArray(String[]::new)).isPresent();
    }

    /** The standard output of a successful run; empty when the command fails. */
    private Optional<String> run(Path work, String... command) throws IOException {
        Path stdout = Files.createTempFile(work, "stdout-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(work.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("VIPS_BLOCK_UNTRUSTED", "1");
        // RNF-21: conversions are bounded by count (T-E1-14); each one uses a single thread.
        builder.environment().put("VIPS_CONCURRENCY", "1");
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("libvips took longer than " + timeout);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while libvips was converting", e);
        }
        if (process.exitValue() != 0) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(stdout, StandardCharsets.UTF_8));
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("Could not remove the temporary directory of an image conversion", e);
        }
    }
}
