package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.repository.details.SteamAppDetailRepository;

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
        SteamAppDetailRepository repo = mock(SteamAppDetailRepository.class);
        SteamAppDetailController controller = new SteamAppDetailController(repo);
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
        SteamAppDetailRepository repo = mock(SteamAppDetailRepository.class);
        SteamAppDetailController controller = new SteamAppDetailController(repo);
        ResponseEntity<String> res = controller.indexHtml();
        assertEquals(200, res.getStatusCode().value());
        assertEquals(MediaType.TEXT_HTML, res.getHeaders().getContentType());
        assertNotNull(res.getBody());
        final var html = res.getBody();
        assertNotNull(html);
        assertTrue(html.contains("Details Index"));
    }

    @Test
    void getDetails_found_and_notFound() {
        SteamAppDetailRepository repo = mock(SteamAppDetailRepository.class);
        SteamAppDetailController controller = new SteamAppDetailController(repo);

        // not found
        when(repo.findById(anyLong())).thenReturn(Optional.empty());
        assertEquals(404, controller.getDetails(42L, new HttpHeaders()).getStatusCode().value());

        // found
        SteamAppDetail detail = Mockito.mock(SteamAppDetail.class);
        when(detail.getDevelopers()).thenReturn(Collections.emptySet());
        when(detail.getPublisher()).thenReturn(Collections.emptySet());
        when(detail.getCategories()).thenReturn(Collections.emptySet());
        when(detail.getGenres()).thenReturn(Collections.emptySet());
        when(detail.getScreenshots()).thenReturn(Collections.emptySet());
        when(detail.getMovies()).thenReturn(Collections.emptySet());
        when(detail.getPriceOverview()).thenReturn(null);
        when(repo.findById(7L)).thenReturn(Optional.of(detail));

        ResponseEntity<SteamAppDetail> ok = controller.getDetails(7L, new HttpHeaders());
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getHeaders().getFirst("Cache-Control"));
        assertSame(detail, ok.getBody());
    }
}


