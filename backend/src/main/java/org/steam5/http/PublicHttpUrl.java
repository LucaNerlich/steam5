package org.steam5.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;

/**
 * SSRF guard for outbound fetches of untrusted (DB/Steam-sourced) URLs.
 *
 * <p>Only http(s) on ports 80/443 is allowed, http is upgraded to https, and
 * every resolved address must be a public unicast address (no loopback,
 * link-local, RFC1918, CGNAT, IPv6 ULA, or multicast).</p>
 */
public final class PublicHttpUrl {

    private PublicHttpUrl() {
    }

    /**
     * Returns an https URI that is safe to open, or empty when the URL must not
     * be fetched.
     */
    public static Optional<URI> httpsTarget(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        final URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        final String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Optional.empty();
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            return Optional.empty();
        }
        final int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return Optional.empty();
        }
        final URI https;
        try {
            final int httpsPort = (port == -1 || port == 80) ? -1 : port;
            https = new URI("https", null, uri.getHost(), httpsPort, uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(https.getHost())) {
                if (isDisallowedAddress(address)) {
                    return Optional.empty();
                }
            }
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
        return Optional.of(https);
    }

    static boolean isDisallowedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        return isCarrierGradeNat(address) || isUniqueLocalIpv6(address);
    }

    /** 100.64.0.0/10 — not classified as site-local by {@link InetAddress}. */
    private static boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address v4)) {
            return false;
        }
        final byte[] octets = v4.getAddress();
        final int second = octets[1] & 0xFF;
        return (octets[0] & 0xFF) == 100 && second >= 64 && second <= 127;
    }

    /** fc00::/7 unique-local — not classified as site-local by {@link InetAddress}. */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address v6)) {
            return false;
        }
        return (v6.getAddress()[0] & 0xFE) == 0xFC;
    }
}
