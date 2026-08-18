package org.steam5.http;

import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicHttpUrlTest {

    @Test
    void rejectsNonHttpSchemes() {
        assertTrue(PublicHttpUrl.httpsTarget("file:///etc/passwd").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("ftp://example.com/a.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("javascript:alert(1)").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget(null).isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("").isEmpty());
    }

    @Test
    void rejectsPrivateAndMetadataIpLiterals() {
        assertTrue(PublicHttpUrl.httpsTarget("https://127.0.0.1/x").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("http://10.0.0.5/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://192.168.1.10/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://172.16.0.1/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("http://169.254.169.254/latest/meta-data").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://100.64.0.1/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://[::1]/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://[fc00::1]/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://[::ffff:127.0.0.1]/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://[::ffff:7f00:1]/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://[::ffff:10.0.0.1]/img.png").isEmpty());
    }

    @Test
    void rejectsNonStandardPortsAndUserInfo() {
        assertTrue(PublicHttpUrl.httpsTarget("https://example.com:8080/img.png").isEmpty());
        assertTrue(PublicHttpUrl.httpsTarget("https://user:pass@example.com/img.png").isEmpty());
    }

    @Test
    void classifiesReservedAddressesWithoutDns() throws UnknownHostException {
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("10.1.2.3")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("192.168.0.1")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("172.31.255.255")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("169.254.169.254")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("100.64.1.1")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("::1")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("fc00::1")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("::ffff:127.0.0.1")));
        assertTrue(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("::ffff:10.0.0.1")));
        final InetAddress mappedPublic = InetAddress.getByName("::ffff:8.8.8.8");
        if (mappedPublic instanceof Inet6Address inet6Address) {
            assertTrue(PublicHttpUrl.isIpv4MappedAddress(inet6Address));
        }
        assertFalse(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(PublicHttpUrl.isDisallowedAddress(InetAddress.getByName("1.1.1.1")));
    }
}
