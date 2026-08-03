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

    private LeaderboardMvRepository.PerfectDayMvRow perfectDayRow(String steamId, String personaName,
                                                                    String avatarFull, String blurdataAvatarFull,
                                                                    String profileUrl, LocalDate gameDate,
                                                                    String appNames) {
        final LeaderboardMvRepository.PerfectDayMvRow r = mock(LeaderboardMvRepository.PerfectDayMvRow.class);
        when(r.getSteamId()).thenReturn(steamId);
        when(r.getPersonaName()).thenReturn(personaName);
        when(r.getAvatarFull()).thenReturn(avatarFull);
        when(r.getBlurdataAvatarFull()).thenReturn(blurdataAvatarFull);
        when(r.getProfileUrl()).thenReturn(profileUrl);
        when(r.getGameDate()).thenReturn(gameDate);
        when(r.getAppNames()).thenReturn(appNames);
        return r;
    }

    @Test
    void getPerfectDays_mapsRowsAndSplitsAppNamesOnCommaSpace() {
        final LeaderboardMvRepository.PerfectDayMvRow row = perfectDayRow(
                "76561198000000001", "Alice", "https://avatar/full.jpg", "data:blur", "https://steamcommunity.com/id/alice",
                LocalDate.of(2026, 1, 15), "Half-Life, Portal 2, Left 4 Dead");
        when(leaderboardMvRepository.findPerfectDays()).thenReturn(List.of(row));

        final List<StatisticsService.PerfectDayEntry> result = service.getPerfectDays();

        assertEquals(1, result.size());
        final StatisticsService.PerfectDayEntry entry = result.get(0);
        assertEquals("76561198000000001", entry.steamId());
        assertEquals("Alice", entry.personaName());
        assertEquals("https://avatar/full.jpg", entry.avatar());
        assertEquals("https://steamcommunity.com/id/alice", entry.profileUrl());
        assertEquals(LocalDate.of(2026, 1, 15), entry.gameDate());
        assertEquals(List.of("Half-Life", "Portal 2", "Left 4 Dead"), entry.appNames());
    }

    @Test
    void getPerfectDays_nullAppNames_mapsToEmptyList() {
        final LeaderboardMvRepository.PerfectDayMvRow row = perfectDayRow(
                "76561198000000002", "Bob", null, null, null, LocalDate.of(2026, 2, 1), null);
        when(leaderboardMvRepository.findPerfectDays()).thenReturn(List.of(row));

        final List<StatisticsService.PerfectDayEntry> result = service.getPerfectDays();

        assertEquals(1, result.size());
        assertEquals(List.of(), result.get(0).appNames());
    }

    @Test
    void getPerfectDays_singleAppName_returnsSingletonList() {
        final LeaderboardMvRepository.PerfectDayMvRow row = perfectDayRow(
                "76561198000000003", "Carol", null, null, null, LocalDate.of(2026, 3, 1), "Portal");
        when(leaderboardMvRepository.findPerfectDays()).thenReturn(List.of(row));

        final List<StatisticsService.PerfectDayEntry> result = service.getPerfectDays();

        assertEquals(List.of("Portal"), result.get(0).appNames());
    }

    @Test
    void getPerfectDays_noRows_returnsEmptyList() {
        when(leaderboardMvRepository.findPerfectDays()).thenReturn(List.of());
        assertEquals(List.of(), service.getPerfectDays());
    }

    @Test
    void getPerfectDays_preservesRepositoryOrdering() {
        final LeaderboardMvRepository.PerfectDayMvRow newer = perfectDayRow(
                "steam-newer", "Newer", null, null, null, LocalDate.of(2026, 5, 1), "Game A");
        final LeaderboardMvRepository.PerfectDayMvRow older = perfectDayRow(
                "steam-older", "Older", null, null, null, LocalDate.of(2026, 4, 1), "Game B");
        // Repository query orders by game_date DESC — the service must not re-sort.
        when(leaderboardMvRepository.findPerfectDays()).thenReturn(List.of(newer, older));

        final List<StatisticsService.PerfectDayEntry> result = service.getPerfectDays();

        assertEquals("steam-newer", result.get(0).steamId());
        assertEquals("steam-older", result.get(1).steamId());
    }
}
