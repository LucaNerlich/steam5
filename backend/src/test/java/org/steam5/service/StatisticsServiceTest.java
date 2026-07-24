package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        final SteamAppDetailRepository detailRepository = mock(SteamAppDetailRepository.class);
        final ReviewsBucketRepository reviewsBucketRepository = mock(ReviewsBucketRepository.class);
        final ReviewGamePickRepository reviewGamePickRepository = mock(ReviewGamePickRepository.class);
        final org.steam5.repository.GuessRepository guessRepository = mock(org.steam5.repository.GuessRepository.class);
        final SeasonService seasonService = mock(SeasonService.class);
        final org.springframework.cache.CacheManager cacheManager = mock(org.springframework.cache.CacheManager.class);
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        service = new StatisticsService(detailRepository, reviewsBucketRepository, reviewGamePickRepository,
                guessRepository, seasonService, cacheManager, leaderboardMvRepository);
    }

    private LeaderboardMvRepository.HardestGameMvRow row(long appId, String name, double avgScore, long players,
                                                          long tooHigh, long tooLow, long total,
                                                          String mostCommonWrongBucket, Long mostCommonWrongBucketCount,
                                                          String actualBucket, LocalDate latestPickDate) {
        final LeaderboardMvRepository.HardestGameMvRow r = mock(LeaderboardMvRepository.HardestGameMvRow.class);
        when(r.getAppId()).thenReturn(appId);
        when(r.getAppName()).thenReturn(name);
        when(r.getAvgScore()).thenReturn(avgScore);
        when(r.getPlayerCount()).thenReturn(players);
        when(r.getTooHighCount()).thenReturn(tooHigh);
        when(r.getTooLowCount()).thenReturn(tooLow);
        when(r.getTotalGuesses()).thenReturn(total);
        when(r.getMostCommonWrongBucket()).thenReturn(mostCommonWrongBucket);
        when(r.getMostCommonWrongBucketCount()).thenReturn(mostCommonWrongBucketCount);
        when(r.getActualBucket()).thenReturn(actualBucket);
        when(r.getLatestPickDate()).thenReturn(latestPickDate);
        return r;
    }

    @Test
    void getHardestGames_mapsRowsAndComputesDeceptionDirection() {
        final LeaderboardMvRepository.HardestGameMvRow overGuessed = row(1L, "Over Game", 1.5, 10,
                7L, 1L, 10L, "10000+", 4L, "1-100", LocalDate.of(2026, 1, 1));
        final LeaderboardMvRepository.HardestGameMvRow underGuessed = row(2L, "Under Game", 2.0, 8,
                1L, 6L, 8L, "1-100", 3L, "10000+", LocalDate.of(2026, 1, 2));
        when(leaderboardMvRepository.findHardestGames()).thenReturn(List.of(overGuessed, underGuessed));

        final List<StatisticsService.HardestGame> result = service.getHardestGames(10);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).appId());
        assertEquals("over", result.get(0).deceptionDirection());
        assertEquals(0.7, result.get(0).deceptionRate());
        assertEquals(2L, result.get(1).appId());
        assertEquals("under", result.get(1).deceptionDirection());
    }

    @Test
    void getHardestGames_respectsLimit() {
        final LeaderboardMvRepository.HardestGameMvRow a = row(1L, "A", 1.0, 5, 0L, 0L, 5L, null, null, "1-100", LocalDate.now());
        final LeaderboardMvRepository.HardestGameMvRow b = row(2L, "B", 1.2, 6, 0L, 0L, 6L, null, null, "1-100", LocalDate.now());
        final LeaderboardMvRepository.HardestGameMvRow c = row(3L, "C", 1.4, 7, 0L, 0L, 7L, null, null, "1-100", LocalDate.now());
        when(leaderboardMvRepository.findHardestGames()).thenReturn(List.of(a, b, c));

        final List<StatisticsService.HardestGame> result = service.getHardestGames(2);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).appId());
        assertEquals(2L, result.get(1).appId());
    }

    @Test
    void getHardestGames_noRows_returnsEmptyList() {
        when(leaderboardMvRepository.findHardestGames()).thenReturn(List.of());
        assertEquals(List.of(), service.getHardestGames(10));
    }
}
