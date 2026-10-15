package org.steam5.domain.details;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.repository.details.CategoryRepository;
import org.steam5.repository.details.DeveloperRepository;
import org.steam5.repository.details.GenreRepository;
import org.steam5.repository.details.PublisherRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.DomainCacheEvictor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SteamAppDetailServiceTest {

    private static final long APP_ID = 1512830L;

    private SteamAppDetailRepository repository;
    private SteamAppDetailService service;

    @BeforeEach
    void setUp() {
        repository = mock(SteamAppDetailRepository.class);
        service = new SteamAppDetailService(repository,
                mock(DeveloperRepository.class),
                mock(PublisherRepository.class),
                mock(GenreRepository.class),
                mock(CategoryRepository.class),
                mock(DomainCacheEvictor.class));
    }

    @Test
    void updatesAnExistingDetailWithoutSavingAManagedEntity() {
        final SteamAppDetail existing = new SteamAppDetail();
        existing.setAppId(APP_ID);
        when(repository.findById(APP_ID)).thenReturn(Optional.of(existing));

        service.upsertFromJson(APP_ID, json("""
                {
                  "name": "Refreshed",
                  "screenshots": [
                    {
                      "path_thumbnail": "https://shared.akamai.steamstatic.com/thumb.jpg",
                      "path_full": "https://shared.akamai.steamstatic.com/full.jpg"
                    }
                  ],
                  "movies": [
                    {
                      "name": "Clip",
                      "thumbnail": "https://shared.akamai.steamstatic.com/clip.jpg",
                      "webm": {"max": "https://shared.akamai.steamstatic.com/clip.webm"}
                    }
                  ]
                }
                """));

        assertEquals("Refreshed", existing.getName());
        assertEquals(1, existing.getScreenshots().size());
        assertEquals(1, existing.getMovies().size());
        // Saving a managed entity would merge it, and Hibernate's merge chokes on the
        // deferred DelayedPostInsertIdentifier of the freshly added screenshots/movies
        // (ClassCastException); dirty checking plus the cascade persist them instead.
        verify(repository, never()).save(any());
    }

    @Test
    void persistsANewDetail() {
        when(repository.findById(APP_ID)).thenReturn(Optional.empty());

        service.upsertFromJson(APP_ID, json("{\"name\": \"New\"}"));

        final ArgumentCaptor<SteamAppDetail> saved = ArgumentCaptor.forClass(SteamAppDetail.class);
        verify(repository).save(saved.capture());
        assertEquals(APP_ID, saved.getValue().getAppId());
        assertEquals("New", saved.getValue().getName());
    }

    private static JsonNode json(final String value) {
        return new ObjectMapper().readTree(value);
    }
}
