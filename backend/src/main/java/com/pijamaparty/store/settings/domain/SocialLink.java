package com.pijamaparty.store.settings.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * UC-36: a link to the store's page on a social network. It is {@code https://}, its host is the network's own, it has
 * a path after the host and no user info or port, and it has at most 200 characters, so a link can never send shoppers
 * to another site.
 */
public record SocialLink(Network network, String url) {

    public static final int MAX_LENGTH = 200;

    /** The networks of the footer, each with the hosts its links may have. */
    public enum Network {
        INSTAGRAM(Set.of("instagram.com", "www.instagram.com")),
        FACEBOOK(Set.of("facebook.com", "www.facebook.com", "m.facebook.com")),
        TIKTOK(Set.of("tiktok.com", "www.tiktok.com"));

        private final Set<String> hosts;

        Network(Set<String> hosts) {
            this.hosts = hosts;
        }
    }

    public SocialLink {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(url, "url");
        if (!valid(network, url)) {
            throw new IllegalArgumentException("not a link to " + network);
        }
    }

    /** The link trimmed, or empty when it breaks the rule of its network. */
    public static Optional<SocialLink> parse(Network network, String typed) {
        Objects.requireNonNull(network, "network");
        String url = Objects.requireNonNullElse(typed, "").strip();
        return valid(network, url) ? Optional.of(new SocialLink(network, url)) : Optional.empty();
    }

    private static boolean valid(Network network, String url) {
        if (url.isEmpty() || url.length() > MAX_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        String host = uri.getHost();
        String path = uri.getRawPath();
        return "https".equalsIgnoreCase(uri.getScheme())
            && uri.getRawUserInfo() == null
            && uri.getPort() == -1
            && host != null && network.hosts.contains(host.toLowerCase(Locale.ROOT))
            // Exactly "https://" and the host: no "https:/host" or "https:///host" that a browser would read otherwise.
            && url.regionMatches(true, 0, "https://" + host + "/", 0, "https://".length() + host.length() + 1)
            && path != null && path.length() > 1;
    }
}
