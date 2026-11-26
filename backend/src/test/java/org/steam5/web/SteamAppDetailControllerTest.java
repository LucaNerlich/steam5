package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.domain.details.SteamAppDetailService;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SteamAppDetailControllerTest {

    @Test
    void indexJson_returnsLinks() {
        SteamAppDetailService detailService = mock(SteamAppDetailService.class);
        SteamAppDetailController controller = new SteamAppDetailController(detailService, new tools.jackson.databind.ObjectMapper());
        ResponseEntity<Map<String, String>> res = controller.indexJson();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        final var body1 = res.getBody();
        assertNotNull(body1);
        assertTrue(body1.containsKey("self"));
        assertTrue(body1.containsKey("byAppId"));
    }

    @Test
    void indexHtml_returnsHtml() {
        SteamAppDetailService detailService = mock(SteamAppDetailService.class);
        SteamAppDetailController controller = new SteamAppDetailController(detailService, new tools.jackson.databind.ObjectMapper());
        ResponseEntity<String> res = controller.indexHtml();
        assertEquals(200, res.getStatusCode().value());
        assertEquals(MediaType.TEXT_HTML, res.getHeaders().getContentType());
        assertNotNull(res.getBody());
        final var html = res.getBody();
        assertNotNull(html);
        assertTrue(html.contains("Details Index"));
    }

    @Test
    void etagChangesWhenOnlyAnUnsampledFieldChanges() throws Exception {
        // The old sampler hashed name/release/price/5 screenshots/3 movies; a description or
        // header-image change kept answering 304 with the stale body.
        final SteamAppDetail a = new SteamAppDetail();
        a.setAppId(1L);
        a.setName("Game");
        a.setShortDescription("before");
        final SteamAppDetail b = new SteamAppDetail();
        b.setAppId(1L);
        b.setName("Game");
        b.setShortDescription("after");

        final java.lang.reflect.Method detail = SteamAppDetailController.class
                .getDeclaredMethod("weakEtagForDetail", SteamAppDetail.class);
        detail.setAccessible(true);
        final SteamAppDetailController controller =
                new SteamAppDetailController(mock(SteamAppDetailService.class), new tools.jackson.databind.ObjectMapper());

        assertNotEquals(detail.invoke(controller, a), detail.invoke(controller, b));
    }

    @Test
    void getDetails_found_and_notFound() {
        SteamAppDetailService detailService = mock(SteamAppDetailService.class);
        SteamAppDetailController controller = new SteamAppDetailController(detailService, new tools.jackson.databind.ObjectMapper());

        // not found
        when(detailService.findByAppId(anyLong())).thenReturn(Optional.empty());
        assertEquals(404, controller.getDetails(42L).getStatusCode().value());

        // found
        SteamAppDetail detail = Mockito.mock(SteamAppDetail.class);
        when(detail.getDevelopers()).thenReturn(Collections.emptySet());
        when(detail.getPublisher()).thenReturn(Collections.emptySet());
        when(detail.getCategories()).thenReturn(Collections.emptySet());
        when(detail.getGenres()).thenReturn(Collections.emptySet());
        when(detail.getScreenshots()).thenReturn(Collections.emptySet());
        when(detail.getMovies()).thenReturn(Collections.emptySet());
        when(detail.getPriceOverview()).thenReturn(null);
        when(detailService.findByAppId(7L)).thenReturn(Optional.of(detail));
        ResponseEntity<SteamAppDetail> ok = controller.getDetails(7L);
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getHeaders().getFirst("Cache-Control"));
        assertSame(detail, ok.getBody());
    }
}


