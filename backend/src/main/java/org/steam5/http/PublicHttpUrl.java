package org.steam5.http;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * SSRF guard for outbound fetches of untrusted (DB/Steam-sourced) URLs.
 *
 * <p>Only http(s) on ports 80/443 is allowed, http is upgraded to https, and
 * every resolved address must be a public unicast address (no loopback,
 * link-local, RFC1918, CGNAT, IPv6 ULA, multicast, 0.0.0.0/8, or
 * IPv4-compatible/mapped private literals). Resolved addresses are re-checked
 * immediately before connect to mitigate DNS rebinding.</p>
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
            if (!hasOnlyAllowedAddresses(https.getHost())) {
                return Optional.empty();
            }
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
        return Optional.of(https);
    }

    /**
     * Opens an {@link HttpsURLConnection} after validating the URL and pinning
     * the TCP connection to a freshly resolved public address (DNS rebinding
     * mitigation).
     */
    public static Optional<HttpURLConnection> openHttpsConnection(String url) throws IOException {
        final Optional<URI> target = httpsTarget(url);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(openHttpsConnection(target.get()));
    }

    static HttpURLConnection openHttpsConnection(URI httpsUri) throws IOException {
        final String host = httpsUri.getHost();
        final int port = httpsUri.getPort() == -1 ? 443 : httpsUri.getPort();
        final InetAddress connectAddress = selectConnectAddress(host);

        final HttpsURLConnection conn = (HttpsURLConnection) httpsUri.toURL().openConnection(Proxy.NO_PROXY);
        conn.setSSLSocketFactory(new PinnedAddressSocketFactory(host, port, connectAddress));
        // No custom HostnameVerifier: the default one validates the peer certificate
        // against the URL host, which is exactly what the pinned connect intends.
        // A verifier that compares the host to itself would disable that check.
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private static InetAddress selectConnectAddress(String host) throws IOException {
        final InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new IOException("Unable to resolve host", ex);
        }
        for (InetAddress address : resolved) {
            if (!isDisallowedAddress(address)) {
                return address;
            }
        }
        throw new IOException("Host resolves only to disallowed addresses");
    }

    private static boolean hasOnlyAllowedAddresses(String host) throws UnknownHostException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isDisallowedAddress(address)) {
                return false;
            }
        }
        return true;
    }

    static boolean isDisallowedAddress(InetAddress address) {
        final InetAddress normalized = normalizeAddress(address);
        if (normalized.isAnyLocalAddress()
                || isThisNetwork(normalized)
                || normalized.isLoopbackAddress()
                || normalized.isLinkLocalAddress()
                || normalized.isSiteLocalAddress()
                || normalized.isMulticastAddress()) {
            return true;
        }
        return isCarrierGradeNat(normalized) || isUniqueLocalIpv6(normalized);
    }

    /**
     * Treat IPv4-mapped ({@code ::ffff:a.b.c.d}) and IPv4-compatible
     * ({@code ::a.b.c.d}) IPv6 literals as the IPv4 address they embed, so a
     * single deny-list pass covers them. The JDK predicates already classify a
     * mapped literal parsed by {@link InetAddress}, but not the compatible
     * form: {@code ::7f00:1} reads as a public address there while Linux routes
     * it to the local host.
     */
    private static InetAddress normalizeAddress(InetAddress address) {
        if (address instanceof Inet6Address v6 && isIpv4EmbeddedAddress(v6)) {
            try {
                return extractEmbeddedIpv4(v6);
            } catch (UnknownHostException ex) {
                return address;
            }
        }
        return address;
    }

    /** {@code ::ffff:0:0/96} (mapped) and {@code ::/96} (compatible) both embed an IPv4 address. */
    static boolean isIpv4EmbeddedAddress(Inet6Address v6) {
        final byte[] octets = v6.getAddress();
        for (int i = 0; i < 10; i++) {
            if (octets[i] != 0) {
                return false;
            }
        }
        final boolean mapped = octets[10] == (byte) 0xff && octets[11] == (byte) 0xff;
        final boolean compatible = octets[10] == 0 && octets[11] == 0;
        return mapped || compatible;
    }

    private static InetAddress extractEmbeddedIpv4(Inet6Address v6) throws UnknownHostException {
        final byte[] octets = v6.getAddress();
        return InetAddress.getByAddress(new byte[]{octets[12], octets[13], octets[14], octets[15]});
    }

    /**
     * 0.0.0.0/8 — "this network" (RFC 1122). Only {@code 0.0.0.0} itself is
     * reported by {@link InetAddress#isAnyLocalAddress()}; Linux routes every
     * other address in the block to the local host.
     */
    private static boolean isThisNetwork(InetAddress address) {
        if (!(address instanceof Inet4Address v4)) {
            return false;
        }
        return (v4.getAddress()[0] & 0xFF) == 0;
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

    /**
     * Connects to a pre-validated IP while preserving SNI/certificate checks for
     * the original hostname.
     */
    private static final class PinnedAddressSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        private final String hostname;
        private final int port;
        private final InetAddress address;

        private PinnedAddressSocketFactory(String hostname, int port, InetAddress address) {
            this.hostname = hostname;
            this.port = port;
            this.address = address;
        }

        @Override
        public Socket createSocket() throws IOException {
            return configure(createPlainSocket(), true);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(createPlainSocket(), true);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(createPlainSocket(), true);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(createPlainSocket(), true);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return configure(createPlainSocket(), true);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return configure(socket, autoClose);
        }

        private Socket createPlainSocket() throws IOException {
            final Socket socket = new Socket(Proxy.NO_PROXY);
            socket.connect(new InetSocketAddress(address, this.port), 15_000);
            return socket;
        }

        private Socket configure(Socket socket, boolean autoClose) throws IOException {
            final SSLSocket ssl = (SSLSocket) delegate.createSocket(socket, hostname, port, autoClose);
            final SSLParameters params = ssl.getSSLParameters();
            params.setServerNames(List.of(new SNIHostName(hostname)));
            ssl.setSSLParameters(params);
            // HttpsURLConnection connects whatever this factory returned, but the
            // socket already is connected to the validated address and SSLSocketImpl
            // rejects a second connect() on a layered socket ("Already connected").
            return new PinnedSslSocket(ssl);
        }
    }
}
