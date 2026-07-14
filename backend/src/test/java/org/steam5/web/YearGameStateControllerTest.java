package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YearGameStateControllerTest {

    private final YearGameStateController controller = new YearGameStateController();

    @Test
    void todayReturnsNotImplemented() {
        final ResponseEntity<Map<String, String>> response = controller.notImplemented();
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertEquals("Release year guesser is not available yet", response.getBody().get("message"));
    }
}
