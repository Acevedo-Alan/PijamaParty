package com.pijamaparty.store.notifications.adapter.in.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs test notifications as Resend does, through Svix (T-E3-22). */
public final class ResendWebhooks {

    /** The webhook secret the application uses in tests: {@code whsec_} and a base64 key. */
    public static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(
        "test-resend-webhook-signing-key!".getBytes(StandardCharsets.UTF_8));

    private ResendWebhooks() {
    }

    /** The {@code svix-signature} header of the notification, signed with {@link #SECRET}. */
    public static String sign(String id, String timestamp, byte[] body) {
        return sign(SECRET, id, timestamp, body);
    }

    /** The {@code svix-signature} header of the notification, signed with {@code secret}. */
    public static String sign(String secret, String id, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.substring("whsec_".length())),
                "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // An empty key cannot sign: any value is as wrong as the next.
            return "v1,AAAA";
        }
    }
}
