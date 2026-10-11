package com.pijamaparty.store.accounts.adapter.out.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.pijamaparty.store.accounts.application.EnrollmentQrCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** A PNG QR code drawn in memory with ZXing (a dependency of the totp library), never written anywhere. */
@Component
class ZxingEnrollmentQrCode implements EnrollmentQrCode {

    private static final int SIZE_PX = 240;

    @Override
    public String dataUri(String otpauthUri) {
        try {
            var matrix = new QRCodeWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX);
            var png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (WriterException e) {
            throw new IllegalStateException("cannot draw the enrollment QR code", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
