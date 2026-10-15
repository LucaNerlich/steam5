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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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

    @Test
    void findByAppId_touchesEveryCollectionSoTheDetachedEntityCanSerialize() {
        final SteamAppDetail spy = spy(new SteamAppDetail());
        when(repository.findByAppId(APP_ID)).thenReturn(Optional.of(spy));

        final Optional<SteamAppDetail> result = service.findByAppId(APP_ID);

        assertSame(spy, result.orElseThrow());
        verifyEveryCollectionTouched(spy);
    }

    @Test
    void findAllByAppIdIn_touchesEveryCollectionSoDetachedEntitiesCanSerialize() {
        final SteamAppDetail spy = spy(new SteamAppDetail());
        when(repository.findAllByAppIdIn(List.of(APP_ID))).thenReturn(List.of(spy));

        final List<SteamAppDetail> result = service.findAllByAppIdIn(List.of(APP_ID));

        assertEquals(1, result.size());
        assertSame(spy, result.getFirst());
        verifyEveryCollectionTouched(spy);
    }

    /**
     * The returns here are made while {@code open-in-view: false} is active, so every
     * collection serialization walks must have been touched in the transaction —
     * accessing them later (or leaving them uninitialized) would throw
     * LazyInitializationException. The graph no longer join-fetches them; Hibernate
     * batches each collection load.
     */
    private static void verifyEveryCollectionTouched(final SteamAppDetail spy) {
        verify(spy).getDevelopers();
        verify(spy).getPublisher();
        verify(spy).getGenres();
        verify(spy).getCategories();
        verify(spy).getScreenshots();
        verify(spy).getMovies();
    }

    private static JsonNode json(final String value) {
        return new ObjectMapper().readTree(value);
    }
}
