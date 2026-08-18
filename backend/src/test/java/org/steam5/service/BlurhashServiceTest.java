package org.steam5.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class BlurhashServiceTest {

    private final BlurhashService service = new BlurhashService();

    @Test
    void readAndEncode_rejectsNonPublicUrlsWithoutOpeningAConnection() {
        assertNull(service.readAndEncode("file:///etc/passwd", BlurhashService.Type.THUMBNAIL));
        assertNull(service.readAndEncode("ftp://example.com/a.png", BlurhashService.Type.THUMBNAIL));
        assertNull(service.readAndEncode("https://127.0.0.1/img.png", BlurhashService.Type.THUMBNAIL));
        assertNull(service.readAndEncode("http://169.254.169.254/latest/meta-data", BlurhashService.Type.THUMBNAIL));
        assertNull(service.readAndEncode("https://example.com:8443/img.png", BlurhashService.Type.THUMBNAIL));
    }
}
