package com.pijamaparty.store.platform.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A browser of the SPA over real HTTP: it keeps the cookies it is given (the session and the CSRF token) and sends
 * the CSRF token back as the {@code X-XSRF-TOKEN} header on every request that changes something.
 */
public final class Browser {

    private final TestRestTemplate http;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public Browser(TestRestTemplate http) {
        this.http = http;
    }

    /** Another browser that starts with these cookies, for example copied before a sign-out. */
    public Browser(TestRestTemplate http, Map<String, String> cookies) {
        this(http);
        this.cookies.putAll(cookies);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public Map<String, String> cookies() {
        return Map.copyOf(cookies);
    }

    public ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    public ResponseEntity<String> post(String path, String json) {
        return exchange(HttpMethod.POST, path, json);
    }

    public ResponseEntity<String> put(String path, String json) {
        return exchange(HttpMethod.PUT, path, json);
    }

    public ResponseEntity<String> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String json) {
        var headers = new HttpHeaders();
        if (!cookies.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, String.join("; ",
                cookies.entrySet().stream().map(cookie -> cookie.getKey() + "=" + cookie.getValue()).toList()));
        }
        if (method != HttpMethod.GET && cookies.containsKey("XSRF-TOKEN")) {
            headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(json, headers), String.class);
        for (String setCookie : response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            String pair = setCookie.split(";", 2)[0];
            String name = pair.substring(0, pair.indexOf('='));
            String value = pair.substring(pair.indexOf('=') + 1);
            if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
        return response;
    }
}
