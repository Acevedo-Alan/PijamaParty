package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImageDimensions;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Photos for the image tests, made in memory, and a reader of WebP dimensions. */
public final class TestImages {

    private TestImages() {
    }

    /** A JPG of random pixels: noise barely compresses, so the file is large for its size. */
    public static byte[] noisyJpeg(int width, int height, float quality) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(width * 31L + height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(quality);
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** A plain PNG with a gradient. */
    public static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8 | 0x80);
            }
        }
        var bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * A PNG whose header states {@code width} × {@code height}, followed by a tiny invalid IDAT: enough to read the
     * dimensions, never enough to decode. Stands for a decompression bomb.
     */
    public static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer header = ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        bytes.writeBytes(chunk("IHDR", header.array()));
        bytes.writeBytes(chunk("IDAT", new byte[] {0x78, (byte) 0x9C, 0, 0, 0}));
        bytes.writeBytes(chunk("IEND", new byte[0]));
        return bytes.toByteArray();
    }

    /** {@code jpeg} with an EXIF block that sets the orientation (6: rotate 90° clockwise to display). */
    public static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
            .put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8)
            .putShort((short) 1)
            .putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0)
            .putInt(0);
        byte[] exif = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff.array());
        int length = exif.length + 2;
        byte[] app1 = concat(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length}, exif);
        byte[] rest = new byte[jpeg.length - 2];
        System.arraycopy(jpeg, 2, rest, 0, rest.length);
        return concat(new byte[] {(byte) 0xFF, (byte) 0xD8}, concat(app1, rest));
    }

    /**
     * A WebP made of its VP8X header only: it states its dimensions but has no pixels. The fake processor writes these,
     * since Java has no WebP encoder.
     */
    public static byte[] webpHeaderOnly(int width, int height) {
        return ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(22).put("WEBP".getBytes(StandardCharsets.US_ASCII))
            .put("VP8X".getBytes(StandardCharsets.US_ASCII)).putInt(10).putInt(0)
            .put(int24(width - 1)).put(int24(height - 1))
            .array();
    }

    /** The dimensions a WebP states (VP8, VP8L or VP8X); empty when it is not a WebP. */
    public static Optional<ImageDimensions> webpDimensions(byte[] webp) {
        if (webp.length < 30 || !ascii(webp, 0, "RIFF") || !ascii(webp, 8, "WEBP")) {
            return Optional.empty();
        }
        ByteBuffer bytes = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN);
        if (ascii(webp, 12, "VP8X")) {
            return Optional.of(new ImageDimensions(uint24(webp, 24) + 1, uint24(webp, 27) + 1));
        }
        if (ascii(webp, 12, "VP8 ")) {
            return Optional.of(new ImageDimensions(bytes.getShort(26) & 0x3FFF, bytes.getShort(28) & 0x3FFF));
        }
        if (ascii(webp, 12, "VP8L")) {
            int bits = bytes.getInt(21);
            return Optional.of(new ImageDimensions((bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1));
        }
        return Optional.empty();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
            .putInt((int) crc.getValue()).array();
    }

    private static byte[] int24(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16)};
    }

    private static int uint24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8 | (bytes[offset + 2] & 0xFF) << 16;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        return new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII).equals(expected);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
