package com.pijamaparty.store.accounts.adapter.in.web;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Google's token and key endpoints and Facebook's Graph API on a local port, so whole-application tests sign in with
 * a provider without the network. The test reads {@code state}, {@code nonce} and {@code code_challenge} from the
 * redirect to the provider and tells the stub which account comes back: the token endpoint refuses a code verifier
 * that does not match the challenge (PKCE), and Google's ID token is signed with the stub's key and carries the nonce.
 */
public final class ProvidersStub {

    public static final String GOOGLE_CLIENT_ID = "google-client-for-tests";
    public static final String FACEBOOK_CLIENT_ID = "facebook-client-for-tests";
    static final String GRAPH_VERSION = "v23.0";
    private static final String FACEBOOK_TOKEN = "facebook-access-token";

    /** The account the provider returns; {@code email} or {@code name} null when it gives none. */
    public record Account(String subject, String email, boolean emailVerified, String name) {}

    private record Next(Account account, String nonce, String codeChallenge) {}

    private final HttpServer server;
    private final RSAKey key;
    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private volatile Next google;
    private volatile Next facebook;

    public ProvidersStub() {
        try {
            key = new RSAKeyGenerator(2048).keyID("stub-key").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException | JOSEException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/google/token", this::googleToken);
        server.createContext("/google/certs", exchange ->
            send(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/facebook/" + GRAPH_VERSION + "/oauth/access_token", this::facebookToken);
        server.createContext("/facebook/" + GRAPH_VERSION + "/me", this::facebookProfile);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** What Google returns next, for the authorization request that carried this nonce and challenge. */
    public void nextGoogle(Account account, String nonce, String codeChallenge) {
        google = new Next(account, nonce, codeChallenge);
    }

    /** What Facebook returns next, for the authorization request that carried this challenge. */
    public void nextFacebook(Account account, String codeChallenge) {
        facebook = new Next(account, null, codeChallenge);
    }

    /** How many codes were exchanged. */
    public int tokenRequests() {
        return tokenRequests.size();
    }

    public void reset() {
        google = null;
        facebook = null;
        tokenRequests.clear();
    }

    public void stop() {
        server.stop(0);
    }

    private void googleToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(exchange);
        tokenRequests.add("google");
        Next next = google;
        if (next == null || !pkceMatches(form.get("code_verifier"), next.codeChallenge())) {
            send(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        var claims = new JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .audience(GOOGLE_CLIENT_ID)
            .subject(next.account().subject())
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .claim("nonce", next.nonce())
            .claim("email", next.account().email())
            .claim("email_verified", next.account().emailVerified())
            .claim("name", next.account().name())
            .build();
        try {
            var idToken = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
            idToken.sign(new RSASSASigner(key));
            send(exchange, 200, "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3599,\"scope\":\"openid profile email\",\"id_token\":\"" + idToken.serialize()
                + "\"}");
        } catch (JOSEException e) {
            throw new IOException(e);
        }
    }

    private void facebookToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(exchange);
        tokenRequests.add("facebook");
        Next next = facebook;
        if (next == null || !pkceMatches(form.get("code_verifier"), next.codeChallenge())) {
            send(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        send(exchange, 200, "{\"access_token\":\"" + FACEBOOK_TOKEN + "\",\"token_type\":\"bearer\","
            + "\"expires_in\":5183944}");
    }

    private void facebookProfile(HttpExchange exchange) throws IOException {
        Next next = facebook;
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (next == null || !("Bearer " + FACEBOOK_TOKEN).equals(authorization)) {
            send(exchange, 401, "{\"error\":{\"message\":\"Invalid OAuth access token\"}}");
            return;
        }
        var profile = new LinkedHashMap<String, String>();
        profile.put("id", next.account().subject());
        if (next.account().name() != null) {
            profile.put("name", next.account().name());
        }
        if (next.account().email() != null) {
            profile.put("email", next.account().email());
        }
        send(exchange, 200, json(profile));
    }

    private static boolean pkceMatches(String verifier, String challenge) {
        if (verifier == null || challenge == null) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).equals(challenge);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var form = new LinkedHashMap<String, String>();
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                form.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

    private static String json(Map<String, String> fields) {
        var json = new StringBuilder("{");
        fields.forEach((name, value) -> json.append(json.length() > 1 ? "," : "")
            .append('"').append(name).append("\":\"").append(value.replace("\"", "\\\"")).append('"'));
        return json.append('}').toString();
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
