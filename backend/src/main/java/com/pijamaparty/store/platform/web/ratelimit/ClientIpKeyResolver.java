package com.pijamaparty.store.platform.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The client IP. With {@code server.forward-headers-strategy=native} Tomcat replaces the remote address with the
 * {@code X-Forwarded-For} value only when the connection comes from a trusted proxy (Caddy), so a header forged by
 * anyone else is ignored (RNF-22). An IPv6 client is keyed by its /64 prefix: one subscriber usually owns a whole
 * /64 and could otherwise rotate addresses to get a fresh bucket every time.
 */
@Component
class ClientIpKeyResolver implements RateLimitKeyResolver {

    private static final int IPV6_PREFIX_BYTES = 8;

    @Override
    public String name() {
        return "ip";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        if (address.indexOf(':') < 0) {
            return Optional.of(address);
        }
        return Optional.of(ipv6Prefix(address));
    }

    private static String ipv6Prefix(String address) {
        try {
            // A literal address: getByName parses it without any DNS lookup.
            InetAddress parsed = InetAddress.getByName(address);
            if (parsed instanceof Inet6Address) {
                byte[] bytes = parsed.getAddress();
                return HexFormat.of().formatHex(bytes, 0, IPV6_PREFIX_BYTES) + "::/64";
            }
            return parsed.getHostAddress();
        } catch (UnknownHostException e) {
            return address;
        }
    }
}
