package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.PlayerSpotlight;
import org.steam5.domain.PlayerSpotlightInsightType;
import org.steam5.domain.StreakCalculator;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks one eligible player each day to feature in a "good vibes" box shown on
 * round 1. Eligibility requires an established, currently-active player (min
 * rounds all-time, played recently) so the spotlight never features a fluke.
 * <p>
 * Selection is a lottery, not a priority ladder: every tier in the fixed-order
 * "competitive pool" (DAY_STREAK, BEST_DAY_EVER, BEAT_THE_ODDS, WELCOME_BACK,
 * MOST_IMPROVED, HOT_STREAK) that has at least one qualifying candidate is
 * added to a list, and one entry is drawn uniformly at random via
 * {@code new Random(today.toEpochDay())}. This means no single ambient tier
 * (e.g. DAY_STREAK, which is easy to qualify for) can dominate just because
 * it's evaluated first — it only gets an edge if it's the ONLY tier that
 * qualifies. But some tiers (DAY_STREAK, HOT_STREAK) are ambient enough that
 * they're often the only one qualifying on a given day, which would still let
 * them repeat night after night. To spread variety, any tier featured within
 * the last {@link #COOLDOWN_WINDOW_DAYS} days is dropped from the pool before
 * the draw — unless doing so would empty the pool, in which case the cooldown
 * is ignored so a spotlight is still produced. The same window also drops any
 * <em>player</em> featured within {@link #COOLDOWN_WINDOW_DAYS} days from the
 * eligible pool entirely (again with an empty-pool bypass), so the same
 * person isn't repeatedly featured just because they're a top performer. When
 * the competitive pool has zero qualifying tiers, two sequential fallbacks
 * are tried in order: WEEKLY_ACHIEVEMENT, then MILESTONE (which always has at
 * least one candidate among the eligible pool, guaranteeing a spotlight is
 * always produced). Ties within a chosen tier are broken by the same
 * date-seeded {@link Random} (seeded via {@link #mixSeed(long)} rather than
 * the raw epoch day, since feeding sequential integers straight into {@link
 * Random}'s linear-congruential generator is known to correlate poorly for
 * small bounds), so the result is stable for the whole day but rotates daily.
 * Headline/detail copy is drawn from small per-tier phrasing pools using an
 * independently-seeded {@link Random} (see {@link #copyRandom(LocalDate,
 * Enum)}) so that wording rotates across days without perturbing which tier
 * or player wins the lottery above.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerSpotlightService {

    /** Shared lookback window for both the tier-repeat and player-repeat cooldowns, so both can be derived from one query. */
    private static final int COOLDOWN_WINDOW_DAYS = 7;
    private static final int MIN_TOTAL_ROUNDS = 70;
    private static final int RECENCY_WINDOW_DAYS = 14;
    private static final int MIN_ROUNDS_IN_RECENCY_WINDOW = 35;
    /** Relaxed eligibility floor used only when nobody clears the normal bar above (see {@link #findEligibleCandidates(LocalDate)}). */
    private static final int LAST_RESORT_MIN_TOTAL_ROUNDS = 1;
    private static final int LAST_RESORT_MIN_ROUNDS_IN_RECENCY_WINDOW = 1;
    private static final int MIN_DAY_STREAK = 5;
    private static final int MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER = 2;
    private static final double HARD_ROUND_MAX_AVG_SCORE = 2.0;
    private static final int BEAT_THE_ODDS_MIN_POINTS = 4;
    private static final int WELCOME_BACK_MIN_GAP_DAYS = 4;
    private static final double WELCOME_BACK_MIN_RETURN_DAY_AVG = 3.0;
    private static final int MOST_IMPROVED_WINDOW_DAYS = 30;
    private static final int MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW = 10;
    private static final double MOST_IMPROVED_RELATIVE_THRESHOLD = 1.15;
    private static final double MOST_IMPROVED_ABSOLUTE_DELTA = 0.3;
    private static final int HOT_STREAK_WINDOW_DAYS = 14;
    private static final int MIN_RECENT_ROUNDS_FOR_HOT_STREAK = 5;
    private static final double HOT_STREAK_RELATIVE_THRESHOLD = 1.2; // recent avg must be >= 20% above all-time avg
    private static final double HOT_STREAK_ABSOLUTE_DELTA = 0.3;    // ...and at least this many points better
    private static final List<Long> ROUND_MILESTONES = List.of(100L, 250L, 500L, 1000L);
    private static final List<Long> POINTS_MILESTONES = List.of(1000L, 5000L, 10000L);
    private static final long MILESTONE_TRAILING_WINDOW = 5;

    // Small per-tier phrasing pools so headline/detail copy rotates across days even when the
    // same tier (or player) recurs. One entry is picked per pool per day via #copyRandom, using
    // a seed independent of the selection lottery above — see the class Javadoc.
    static final List<String> DAY_STREAK_HEADLINES = List.of(
            "On a hot streak!", "Can't stop, won't stop!", "Streak mode: activated!");
    static final List<String> DAY_STREAK_PERSONAL_BEST_DETAILS = List.of(
            "Riding a personal-best %d-day streak — playing every single day!",
            "New personal record: %d days in a row without missing one!",
            "%d days straight and counting — their best run yet!");
    static final List<String> DAY_STREAK_DETAILS = List.of(
            "On a %d-day streak of playing every day.",
            "Hasn't missed a day in %d days.",
            "Keeping the streak alive — %d days and counting.");

    static final List<String> BEST_DAY_EVER_HEADLINES = List.of(
            "Best day ever!", "New high score!", "Personal best unlocked!");
    static final List<String> BEST_DAY_EVER_DETAILS = List.of(
            "Scored %d points yesterday — a new personal best, beating their previous high of %d.",
            "New high score! %d points yesterday, topping their old record of %d.",
            "Set a new personal best yesterday with %d points, past their previous high of %d.");

    static final List<String> BEAT_THE_ODDS_HEADLINES = List.of(
            "Beat the odds!", "Against all odds!", "Odds? Beaten.");
    static final List<String> BEAT_THE_ODDS_DETAILS = List.of(
            "Nailed yesterday's toughest round (round %d, %.1f avg pts across all players) with %d points.",
            "Round %d was brutal (%.1f avg pts across all players) — they still pulled off %d points.",
            "Cracked the hardest round of the day (round %d, %.1f avg pts) for %d points.");

    static final List<String> WELCOME_BACK_HEADLINES = List.of(
            "Welcome back!", "Look who's back!", "The comeback!");
    static final List<String> WELCOME_BACK_DETAILS = List.of(
            "Took a %d-day break and came back strong, averaging %.1f pts/round on their return.",
            "Back after %d days away — averaged %.1f pts/round on their return day.",
            "%d days off didn't slow them down: %.1f pts/round on their comeback.");

    static final List<String> MOST_IMPROVED_HEADLINES = List.of(
            "Most improved!", "Leveling up!", "On the rise!");
    static final List<String> MOST_IMPROVED_DETAILS = List.of(
            "Leveled up: averaging %.1f pts/round over the last %d days, up from %.1f the month before.",
            "On the rise — %.1f pts/round over the last %d days, up from %.1f before.",
            "Big jump in form: %.1f pts/round these last %d days, versus %.1f previously.");

    static final List<String> HOT_STREAK_HEADLINES = List.of(
            "In red-hot form!", "On fire!", "Red hot right now!");
    static final List<String> HOT_STREAK_DETAILS = List.of(
            "Averaging %.1f pts/round over the last %d days — well above their usual %.1f.",
            "In red-hot form: %.1f pts/round over the last %d days, way past their usual %.1f.",
            "%.1f pts/round these last %d days — comfortably ahead of their usual %.1f.");

    static final List<String> MILESTONE_HEADLINES = List.of(
            "A steady presence!", "Rock solid, day after day!", "Always shows up!");
    static final List<String> MILESTONE_JUST_CROSSED_DETAILS = List.of(
            "Just crossed %,d %s — now at %,d!",
            "Milestone alert: %,d %s reached, now sitting at %,d!",
            "Just tipped over %,d %s, now at %,d and climbing!");
    static final List<String> MILESTONE_CLOSING_IN_DETAILS = List.of(
            "Closing in on %,d %s — only %,d away!",
            "So close to %,d %s — just %,d to go!",
            "%,d %s within reach — only %,d left!");
    static final List<String> MILESTONE_GENERIC_DETAILS = List.of(
            "Has played %d rounds and counting, averaging %.1f pts/round.",
            "Racked up %d rounds so far, averaging %.1f pts/round.",
            "%d rounds deep and counting, at %.1f pts/round average.");

    static final List<String> EARLY_BIRD_HEADLINES = List.of(
            "Early bird of the week!", "First up this week!", "Rise-and-shine champion!");
    static final List<String> EARLY_BIRD_DETAILS = List.of(
            "Plays earlier in the day than anyone else this week.",
            "Beats everyone else to the round this week.",
            "First to play, week after week — well, this week.");

    static final List<String> NIGHT_OWL_HEADLINES = List.of(
            "Night owl of the week!", "Latest player standing!", "Burning the midnight oil!");
    static final List<String> NIGHT_OWL_DETAILS = List.of(
            "Plays later in the day than anyone else this week.",
            "Outlasts everyone else this week.",
            "Still playing after everyone else has logged off this week.");

    static final List<String> SHARPSHOOTER_HEADLINES = List.of(
            "Sharpshooter of the week!", "Deadeye of the week!", "Precision player of the week!");
    static final List<String> SHARPSHOOTER_DETAILS = List.of(
            "Highest average score this week — %.1f pts/round.",
            "Tops the leaderboard this week at %.1f pts/round.",
            "Leading the pack this week with %.1f pts/round.");

    static final List<String> BULLSEYE_HEADLINES = List.of(
            "Bullseye streak!", "Perfect precision!", "Dead center, every time!");
    static final List<String> BULLSEYE_DETAILS = List.of(
            "Landed %d perfect round%s this week.",
            "Racked up %d perfect round%s this week.",
            "Nailed %d perfect round%s this week.");

    static final List<String> PERFECT_DAY_HEADLINES = List.of(
            "Flawless days!", "Nothing but perfect!", "Perfect, from start to finish!");
    static final List<String> PERFECT_DAY_DETAILS = List.of(
            "Notched %d perfect day%s this week.",
            "Turned in %d flawless day%s this week.",
            "Racked up %d perfect day%s this week.");

    static final List<String> CHEETAH_HEADLINES = List.of(
            "Quickest hands this week!", "Fastest fingers around!", "Speed demon of the week!");
    static final List<String> CHEETAH_DETAILS = List.of(
            "Blazed through rounds faster than anyone else this week.",
            "Nobody answers quicker this week.",
            "The fastest guesses of the week, hands down.");

    private final GuessRepository guessRepository;
    private final UserRepository userRepository;
    private final StatisticsService statisticsService;
    private final PlayerSpotlightRepository playerSpotlightRepository;

    /**
     * Computes and persists today's spotlight, if one doesn't already exist.
     * Intended to be called once nightly by PlayerSpotlightJob.
     */
    @Transactional
    public void computeAndPersistForToday() {
        final LocalDate today = GameDate.todayUtc();
        if (playerSpotlightRepository.existsById(today)) {
            log.info("PlayerSpotlight already computed for {}", today);
            return;
        }

        final Optional<PlayerSpotlight> spotlight = compute(today);
        if (spotlight.isPresent()) {
            playerSpotlightRepository.save(spotlight.get());
            log.info("PlayerSpotlight computed for {}: steamId={} insight={}",
                    today, spotlight.get().getSteamId(), spotlight.get().getInsightType());
        } else {
            log.info("No eligible PlayerSpotlight candidate for {}", today);
        }
    }

    @Cacheable(value = "stats-short", key = "'spotlight-today:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || !#result.isPresent()")
    public Optional<SpotlightResponse> getTodaySpotlight() {
        return playerSpotlightRepository.findById(GameDate.todayUtc()).map(this::toResponse);
    }

    /** Condensed history (most recent 10) of spotlights a player has been featured in, for their profile page. */
    @Transactional(readOnly = true)
    @Cacheable(value = "player-spotlights", key = "#steamId")
    public List<SpotlightHistoryEntry> listSpotlightsForPlayer(final String steamId) {
        return playerSpotlightRepository.findTop10BySteamIdOrderByGameDateDesc(steamId).stream()
                .map(s -> new SpotlightHistoryEntry(s.getGameDate(), s.getInsightType(), s.getHeadline(),
                        s.getDetail(), s.getStatLabel(), s.getStatValue()))
                .toList();
    }

    private SpotlightResponse toResponse(final PlayerSpotlight spotlight) {
        final User user = userRepository.findById(spotlight.getSteamId()).orElse(null);
        return new SpotlightResponse(
                spotlight.getSteamId(),
                user != null && user.getPersonaName() != null ? user.getPersonaName() : spotlight.getSteamId(),
                user != null ? user.getAvatar() : null,
                spotlight.getInsightType(),
                spotlight.getHeadline(),
                spotlight.getDetail(),
                spotlight.getStatLabel(),
                spotlight.getStatValue()
        );
    }

    private Optional<PlayerSpotlight> compute(final LocalDate today) {
        final List<Candidate> rawEligible = findEligibleCandidates(today);
        if (rawEligible.isEmpty()) {
            return Optional.empty();
        }

        // One query covers both cooldowns (same COOLDOWN_WINDOW_DAYS window): which insight
        // types were recently featured (skipped by the tier lottery below) and which players
        // were recently featured (excluded from the pool entirely, right here).
        final List<PlayerSpotlight> recentSpotlights = playerSpotlightRepository.findByGameDateBetween(
                today.minusDays(COOLDOWN_WINDOW_DAYS), today.minusDays(1));
        final List<Candidate> eligible = excludeRecentlyFeaturedPlayers(rawEligible, recentSpotlights);

        // Batch-prefetch every eligible candidate's guess history in one query, instead of
        // each tier evaluator querying per-candidate — BEST_DAY_EVER, WELCOME_BACK,
        // MOST_IMPROVED, and HOT_STREAK all just filter/aggregate this same in-memory map
        // to their own date window, eliminating what was an N+1 (and 2N for MOST_IMPROVED)
        // pattern across those four tiers.
        final List<String> eligibleIds = eligible.stream().map(Candidate::steamId).toList();
        final Map<String, List<Guess>> historyByPlayer = guessRepository.findBySteamIdIn(eligibleIds).stream()
                .collect(Collectors.groupingBy(Guess::getSteamId));

        // "Competitive pool": every tier here that has >=1 qualifying candidate goes
        // into a lottery, so no single ambient tier (e.g. DAY_STREAK) can dominate
        // just because it's the easiest to qualify for on any given day.
        final LocalDate yesterday = today.minusDays(1);
        final List<QualifyingTier> qualifying = new ArrayList<>();
        addIfQualifying(qualifying, PlayerSpotlightInsightType.DAY_STREAK, evaluateDayStreakTier(eligible, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEST_DAY_EVER, evaluateBestDayEverTier(eligible, yesterday, historyByPlayer, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEAT_THE_ODDS, evaluateBeatTheOddsTier(eligible, yesterday, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.WELCOME_BACK, evaluateWelcomeBackTier(eligible, historyByPlayer, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.MOST_IMPROVED, evaluateMostImprovedTier(eligible, today, historyByPlayer));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.HOT_STREAK, evaluateHotStreakTier(eligible, today, historyByPlayer));

        if (!qualifying.isEmpty()) {
            final Set<PlayerSpotlightInsightType> recentlyFeatured = recentlyFeaturedInsightTypes(recentSpotlights);
            final List<QualifyingTier> freshQualifying = qualifying.stream()
                    .filter(tier -> !recentlyFeatured.contains(tier.type()))
                    .toList();
            final List<QualifyingTier> pool = freshQualifying.isEmpty() ? qualifying : freshQualifying;

            final Random random = new Random(mixSeed(today.toEpochDay()));
            final QualifyingTier chosen = pool.get(random.nextInt(pool.size()));
            return Optional.of(toEntity(today, pickOne(chosen.candidates(), random)));
        }

        final List<Tiered> achievementTier = evaluateWeeklyAchievementTier(eligible, today);
        if (!achievementTier.isEmpty()) {
            return Optional.of(toEntity(today, pickOne(achievementTier, new Random(mixSeed(today.toEpochDay())))));
        }

        // Guaranteed fallback: always show someone among the eligible pool.
        final List<Tiered> milestoneTier = evaluateMilestoneTier(eligible, today);
        return Optional.of(toEntity(today, pickOne(milestoneTier, new Random(mixSeed(today.toEpochDay())))));
    }

    private void addIfQualifying(final List<QualifyingTier> qualifying, final PlayerSpotlightInsightType type,
                                  final List<Tiered> candidates) {
        if (!candidates.isEmpty()) {
            qualifying.add(new QualifyingTier(type, candidates));
        }
    }

    /** Insight types among the given (already-fetched) recent spotlights, so the lottery can skip them. */
    private Set<PlayerSpotlightInsightType> recentlyFeaturedInsightTypes(final List<PlayerSpotlight> recentSpotlights) {
        return recentSpotlights.stream()
                .map(PlayerSpotlight::getInsightType)
                .collect(Collectors.toSet());
    }

    /**
     * Drops candidates featured in any of the given (already-fetched) recent spotlights, so the
     * same player isn't featured night after night — unless doing so would leave no candidates
     * at all, in which case the cooldown is ignored (mirrors the tier cooldown's bypass rule).
     */
    private List<Candidate> excludeRecentlyFeaturedPlayers(final List<Candidate> eligible,
                                                            final List<PlayerSpotlight> recentSpotlights) {
        final Set<String> recentlyFeaturedSteamIds = recentSpotlights.stream()
                .map(PlayerSpotlight::getSteamId)
                .collect(Collectors.toSet());
        final List<Candidate> fresh = eligible.stream()
                .filter(c -> !recentlyFeaturedSteamIds.contains(c.steamId()))
                .toList();
        return fresh.isEmpty() ? eligible : fresh;
    }

    /**
     * Finds today's eligible pool, trying the normal "established, currently active" bar first
     * and only falling back to a relaxed one if literally nobody clears it (e.g. very early in
     * the game's life). The relaxed pass can't fabricate an implausible claim — every downstream
     * tier still applies its own qualification thresholds — it only widens who's considered.
     */
    private List<Candidate> findEligibleCandidates(final LocalDate today) {
        final List<Candidate> established = findEligibleCandidates(today, MIN_TOTAL_ROUNDS, MIN_ROUNDS_IN_RECENCY_WINDOW);
        if (!established.isEmpty()) {
            return established;
        }
        return findEligibleCandidates(today, LAST_RESORT_MIN_TOTAL_ROUNDS, LAST_RESORT_MIN_ROUNDS_IN_RECENCY_WINDOW);
    }

    private List<Candidate> findEligibleCandidates(final LocalDate today, final long minTotalRounds,
                                                     final long minRoundsInRecencyWindow) {
        final Map<String, GuessRepository.AllTimeStatsRow> eligibleAllTime = guessRepository.aggregateAllTimeStats()
                .stream()
                .filter(row -> row.getRounds() != null && row.getRounds() >= minTotalRounds)
                .collect(Collectors.toMap(GuessRepository.AllTimeStatsRow::getSteamId, Function.identity()));

        if (eligibleAllTime.isEmpty()) {
            return List.of();
        }

        final List<String> candidateIds = new ArrayList<>(eligibleAllTime.keySet());
        final Map<String, List<LocalDate>> datesByUser = guessRepository
                .findDistinctDatesUpToForUsers(candidateIds, today).stream()
                .collect(Collectors.groupingBy(
                        GuessRepository.UserDateRow::getSteamId,
                        LinkedHashMap::new,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())
                ));

        // Established (>=70 all-time, or the relaxed floor above) isn't enough on its own — a
        // long-dormant veteran who banked those rounds long ago, then plays a single light day,
        // would otherwise still qualify. Require genuine current activity within the last 14
        // days too. This subsumes the old "played on at least one day in the last 14 days" check
        // (any candidate clearing a >0 round-count floor necessarily has at least one date in the
        // window too), so that separate date check is removed.
        final LocalDate recencyWindowStart = today.minusDays(RECENCY_WINDOW_DAYS - 1L);
        final Map<String, Long> recentRoundsBySteamId = guessRepository
                .findBySteamIdInAndGameDateBetween(candidateIds, recencyWindowStart, today).stream()
                .collect(Collectors.groupingBy(Guess::getSteamId, Collectors.counting()));

        final List<Candidate> eligible = new ArrayList<>();
        for (final String steamId : candidateIds) {
            final long roundsInWindow = recentRoundsBySteamId.getOrDefault(steamId, 0L);
            if (roundsInWindow < minRoundsInRecencyWindow) {
                continue;
            }
            final List<LocalDate> datesDesc = datesByUser.getOrDefault(steamId, List.of());
            eligible.add(new Candidate(steamId, eligibleAllTime.get(steamId), datesDesc));
        }
        return eligible;
    }

    private List<Tiered> evaluateDayStreakTier(final List<Candidate> eligible, final LocalDate today) {
        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.DAY_STREAK);
        final String headline = pickPhrase(DAY_STREAK_HEADLINES, copyRandom);
        final String personalBestTemplate = pickPhrase(DAY_STREAK_PERSONAL_BEST_DETAILS, copyRandom);
        final String plainStreakTemplate = pickPhrase(DAY_STREAK_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final int currentStreak = StreakCalculator.currentStreak(c.datesDesc(), today);
            if (currentStreak < MIN_DAY_STREAK) continue;

            final List<LocalDate> datesAsc = new ArrayList<>(c.datesDesc());
            Collections.reverse(datesAsc);
            final long longest = StreakCalculator.longestStreak(datesAsc);
            final boolean isPersonalBest = currentStreak >= longest;

            final String detail = String.format(isPersonalBest ? personalBestTemplate : plainStreakTemplate, currentStreak);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.DAY_STREAK,
                    headline, detail, "Day streak", (double) currentStreak));
        }
        return tier;
    }

    private List<Tiered> evaluateBestDayEverTier(final List<Candidate> eligible, final LocalDate yesterday,
                                                  final Map<String, List<Guess>> historyByPlayer, final LocalDate today) {
        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.BEST_DAY_EVER);
        final String headline = pickPhrase(BEST_DAY_EVER_HEADLINES, copyRandom);
        final String detailTemplate = pickPhrase(BEST_DAY_EVER_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final Map<LocalDate, Integer> dailyTotals = history.stream()
                    .collect(Collectors.groupingBy(Guess::getGameDate, Collectors.summingInt(Guess::getPoints)));

            final Integer yesterdayTotal = dailyTotals.get(yesterday);
            if (yesterdayTotal == null) continue;

            final List<Integer> priorTotals = dailyTotals.entrySet().stream()
                    .filter(e -> !e.getKey().equals(yesterday))
                    .map(Map.Entry::getValue)
                    .toList();
            if (priorTotals.size() < MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER) continue;

            final int previousBest = Collections.max(priorTotals);
            if (yesterdayTotal <= previousBest) continue;

            final String detail = String.format(detailTemplate, yesterdayTotal, previousBest);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.BEST_DAY_EVER,
                    headline, detail, "Yesterday's points", (double) yesterdayTotal));
        }
        return tier;
    }

    private List<Tiered> evaluateBeatTheOddsTier(final List<Candidate> eligible, final LocalDate yesterday, final LocalDate today) {
        final List<GuessRepository.RoundAvgScoreRow> rows = guessRepository.findRoundAvgScoresInRange(yesterday, yesterday);
        final Optional<GuessRepository.RoundAvgScoreRow> hardestRound = rows.stream()
                .min(Comparator.comparing(GuessRepository.RoundAvgScoreRow::getAvgScore));
        if (hardestRound.isEmpty() || hardestRound.get().getAvgScore() >= HARD_ROUND_MAX_AVG_SCORE) {
            return List.of();
        }

        final int roundIndex = hardestRound.get().getRoundIndex();
        final double hardAvg = hardestRound.get().getAvgScore();

        // One query for everyone's guess on the hardest round, instead of one query per
        // candidate for findBySteamIdAndGameDateAndRoundIndex.
        final Map<String, Guess> guessByPlayer = guessRepository.findByGameDateAndRoundIndex(yesterday, roundIndex)
                .stream()
                .collect(Collectors.toMap(Guess::getSteamId, Function.identity(), (a, b) -> a));

        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.BEAT_THE_ODDS);
        final String headline = pickPhrase(BEAT_THE_ODDS_HEADLINES, copyRandom);
        final String detailTemplate = pickPhrase(BEAT_THE_ODDS_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final Guess theirGuess = guessByPlayer.get(c.steamId());
            if (theirGuess == null || theirGuess.getPoints() < BEAT_THE_ODDS_MIN_POINTS) continue;

            final String detail = String.format(detailTemplate, roundIndex, hardAvg, theirGuess.getPoints());
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.BEAT_THE_ODDS,
                    headline, detail, "Round points", (double) theirGuess.getPoints()));
        }
        return tier;
    }

    private List<Tiered> evaluateWelcomeBackTier(final List<Candidate> eligible,
                                                  final Map<String, List<Guess>> historyByPlayer, final LocalDate today) {
        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.WELCOME_BACK);
        final String headline = pickPhrase(WELCOME_BACK_HEADLINES, copyRandom);
        final String detailTemplate = pickPhrase(WELCOME_BACK_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<LocalDate> datesDesc = c.datesDesc();
            if (datesDesc.size() < 2) continue;

            final LocalDate mostRecent = datesDesc.get(0);
            final LocalDate previous = datesDesc.get(1);
            final long gapDays = ChronoUnit.DAYS.between(previous, mostRecent);
            if (gapDays < WELCOME_BACK_MIN_GAP_DAYS) continue;

            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final double returnDayAvg = history.stream()
                    .filter(g -> g.getGameDate().equals(mostRecent))
                    .mapToInt(Guess::getPoints)
                    .average()
                    .orElse(0.0);
            if (returnDayAvg < WELCOME_BACK_MIN_RETURN_DAY_AVG) continue;

            final String detail = String.format(detailTemplate, gapDays, returnDayAvg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.WELCOME_BACK,
                    headline, detail, "Days away", (double) gapDays));
        }
        return tier;
    }

    private List<Tiered> evaluateMostImprovedTier(final List<Candidate> eligible, final LocalDate today,
                                                   final Map<String, List<Guess>> historyByPlayer) {
        final LocalDate last30Start = today.minusDays(MOST_IMPROVED_WINDOW_DAYS);
        final LocalDate last30End = today.minusDays(1);
        final LocalDate prior30Start = today.minusDays(2L * MOST_IMPROVED_WINDOW_DAYS);
        final LocalDate prior30End = today.minusDays(MOST_IMPROVED_WINDOW_DAYS + 1L);

        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.MOST_IMPROVED);
        final String headline = pickPhrase(MOST_IMPROVED_HEADLINES, copyRandom);
        final String detailTemplate = pickPhrase(MOST_IMPROVED_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final List<Guess> last30 = history.stream()
                    .filter(g -> !g.getGameDate().isBefore(last30Start) && !g.getGameDate().isAfter(last30End))
                    .toList();
            final List<Guess> prior30 = history.stream()
                    .filter(g -> !g.getGameDate().isBefore(prior30Start) && !g.getGameDate().isAfter(prior30End))
                    .toList();
            if (last30.size() < MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW
                    || prior30.size() < MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW) {
                continue;
            }

            final double last30Avg = last30.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            final double prior30Avg = prior30.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            final boolean qualifies = last30Avg >= prior30Avg * MOST_IMPROVED_RELATIVE_THRESHOLD
                    && (last30Avg - prior30Avg) >= MOST_IMPROVED_ABSOLUTE_DELTA;
            if (!qualifies) continue;

            final String detail = String.format(detailTemplate, last30Avg, MOST_IMPROVED_WINDOW_DAYS, prior30Avg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.MOST_IMPROVED,
                    headline, detail, "Last-30d avg", last30Avg));
        }
        return tier;
    }

    private List<Tiered> evaluateWeeklyAchievementTier(final List<Candidate> eligible, final LocalDate today) {
        final Set<String> eligibleIds = eligible.stream().map(Candidate::steamId).collect(Collectors.toSet());
        final List<Tiered> tier = new ArrayList<>();
        for (final StatisticsService.UserLabel label : statisticsService.getUserAchievementsWeekly()) {
            if (!eligibleIds.contains(label.steamId())) continue;
            final AchievementText text = describeAchievement(label, today);
            if (text == null) continue;
            tier.add(new Tiered(label.steamId(), PlayerSpotlightInsightType.WEEKLY_ACHIEVEMENT,
                    text.headline(), text.detail(), text.statLabel(), text.statValue()));
        }
        return tier;
    }

    private List<Tiered> evaluateHotStreakTier(final List<Candidate> eligible, final LocalDate today,
                                                final Map<String, List<Guess>> historyByPlayer) {
        final LocalDate windowStart = today.minusDays(HOT_STREAK_WINDOW_DAYS - 1L);
        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.HOT_STREAK);
        final String headline = pickPhrase(HOT_STREAK_HEADLINES, copyRandom);
        final String detailTemplate = pickPhrase(HOT_STREAK_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final List<Guess> recent = history.stream()
                    .filter(g -> !g.getGameDate().isBefore(windowStart) && !g.getGameDate().isAfter(today))
                    .toList();
            if (recent.size() < MIN_RECENT_ROUNDS_FOR_HOT_STREAK) continue;

            final double allTimeAvg = c.allTime().getAvgPoints() != null ? c.allTime().getAvgPoints() : 0.0;
            if (allTimeAvg <= 0) continue;

            final double recentAvg = recent.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            final boolean qualifies = recentAvg >= allTimeAvg * HOT_STREAK_RELATIVE_THRESHOLD
                    && (recentAvg - allTimeAvg) >= HOT_STREAK_ABSOLUTE_DELTA;
            if (!qualifies) continue;

            final String detail = String.format(detailTemplate, recentAvg, HOT_STREAK_WINDOW_DAYS, allTimeAvg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.HOT_STREAK,
                    headline, detail, "Recent avg", recentAvg));
        }
        return tier;
    }

    private List<Tiered> evaluateMilestoneTier(final List<Candidate> eligible, final LocalDate today) {
        final Random copyRandom = copyRandom(today, PlayerSpotlightInsightType.MILESTONE);
        final String headline = pickPhrase(MILESTONE_HEADLINES, copyRandom);
        final String justCrossedTemplate = pickPhrase(MILESTONE_JUST_CROSSED_DETAILS, copyRandom);
        final String closingInTemplate = pickPhrase(MILESTONE_CLOSING_IN_DETAILS, copyRandom);
        final String genericTemplate = pickPhrase(MILESTONE_GENERIC_DETAILS, copyRandom);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final long rounds = c.allTime().getRounds();
            final long totalPoints = c.allTime().getTotalPoints() != null ? c.allTime().getTotalPoints() : 0L;
            final double avgPoints = c.allTime().getAvgPoints() != null ? c.allTime().getAvgPoints() : 0.0;

            final String detail = nearestMilestoneDetail(rounds, ROUND_MILESTONES, "rounds", justCrossedTemplate, closingInTemplate)
                    .or(() -> nearestMilestoneDetail(totalPoints, POINTS_MILESTONES, "lifetime points", justCrossedTemplate, closingInTemplate))
                    .orElseGet(() -> String.format(genericTemplate, rounds, avgPoints));

            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.MILESTONE,
                    headline, detail, "Rounds played", (double) rounds));
        }
        return tier;
    }

    private Optional<String> nearestMilestoneDetail(final long value, final List<Long> milestones, final String unitLabel,
                                                      final String justCrossedTemplate, final String closingInTemplate) {
        return milestones.stream()
                .filter(milestone -> Math.abs(value - milestone) <= MILESTONE_TRAILING_WINDOW)
                .findFirst()
                .map(milestone -> value >= milestone
                        ? String.format(justCrossedTemplate, milestone, unitLabel, value)
                        : String.format(closingInTemplate, milestone, unitLabel, milestone - value));
    }

    private AchievementText describeAchievement(final StatisticsService.UserLabel label, final LocalDate today) {
        final Random copyRandom = copyRandom(today, label.userAchievement());
        return switch (label.userAchievement()) {
            case EARLY_BIRD -> new AchievementText(pickPhrase(EARLY_BIRD_HEADLINES, copyRandom),
                    pickPhrase(EARLY_BIRD_DETAILS, copyRandom), "Avg time", label.avgMinutes());
            case NIGHT_OWL -> new AchievementText(pickPhrase(NIGHT_OWL_HEADLINES, copyRandom),
                    pickPhrase(NIGHT_OWL_DETAILS, copyRandom), "Avg time", label.avgMinutes());
            case SHARPSHOOTER -> new AchievementText(pickPhrase(SHARPSHOOTER_HEADLINES, copyRandom),
                    String.format(pickPhrase(SHARPSHOOTER_DETAILS, copyRandom), label.avgPoints()),
                    "Avg points", label.avgPoints());
            case BULLSEYE -> {
                final String suffix = label.perfectRounds() != null && label.perfectRounds() == 1 ? "" : "s";
                yield new AchievementText(pickPhrase(BULLSEYE_HEADLINES, copyRandom),
                        String.format(pickPhrase(BULLSEYE_DETAILS, copyRandom), label.perfectRounds(), suffix),
                        "Perfect rounds", label.perfectRounds() != null ? label.perfectRounds().doubleValue() : null);
            }
            case PERFECT_DAY -> {
                final String suffix = label.perfectDays() != null && label.perfectDays() == 1 ? "" : "s";
                yield new AchievementText(pickPhrase(PERFECT_DAY_HEADLINES, copyRandom),
                        String.format(pickPhrase(PERFECT_DAY_DETAILS, copyRandom), label.perfectDays(), suffix),
                        "Perfect days", label.perfectDays() != null ? label.perfectDays().doubleValue() : null);
            }
            case CHEETAH -> new AchievementText(pickPhrase(CHEETAH_HEADLINES, copyRandom),
                    pickPhrase(CHEETAH_DETAILS, copyRandom), "Total seconds",
                    label.totalSeconds() != null ? label.totalSeconds().doubleValue() : null);
            // SLOTH ("slowest player") isn't a good-vibes framing — skip it.
            case SLOTH -> null;
        };
    }

    /** Stable-for-the-day pick among candidates tied in the same tier, using the caller's Random. */
    private Tiered pickOne(final List<Tiered> tier, final Random random) {
        final List<Tiered> sorted = tier.stream()
                .sorted(Comparator.comparing(Tiered::steamId))
                .toList();
        return sorted.get(random.nextInt(sorted.size()));
    }

    /**
     * Bit-mixes an epoch day into a well-distributed seed (SplitMix64's finalizer). Feeding
     * sequential integers straight into {@code new Random(long)} is known to correlate poorly
     * for the kind of small-bound {@code nextInt} calls this class relies on (picking among 2-3
     * tiers/candidates), which is what actually made small, stable candidate pools feel "stuck"
     * on the same winner — not the tie-break's sort-then-pick, which is already uniform. Still
     * fully deterministic per day.
     */
    static long mixSeed(final long epochDay) {
        long z = epochDay + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * An independently-seeded {@link Random} for picking headline/detail copy, salted by the
     * given tier/achievement enum so it never shares a draw sequence with the selection lottery
     * or tie-break — otherwise, resizing a phrasing pool would silently reshuffle who wins on
     * unrelated days. Stable for the day, rotates across days.
     */
    static Random copyRandom(final LocalDate today, final Enum<?> salt) {
        return new Random(mixSeed(today.toEpochDay()) ^ (salt.ordinal() + 1));
    }

    private static String pickPhrase(final List<String> pool, final Random random) {
        return pool.get(random.nextInt(pool.size()));
    }

    private PlayerSpotlight toEntity(final LocalDate today, final Tiered tiered) {
        final PlayerSpotlight entity = new PlayerSpotlight();
        entity.setGameDate(today);
        entity.setSteamId(tiered.steamId());
        entity.setInsightType(tiered.insightType());
        entity.setHeadline(tiered.headline());
        entity.setDetail(tiered.detail());
        entity.setStatLabel(tiered.statLabel());
        entity.setStatValue(tiered.statValue());
        return entity;
    }

    private record Candidate(String steamId, GuessRepository.AllTimeStatsRow allTime, List<LocalDate> datesDesc) {
    }

    private record Tiered(String steamId, PlayerSpotlightInsightType insightType, String headline, String detail,
                           String statLabel, Double statValue) {
    }

    private record QualifyingTier(PlayerSpotlightInsightType type, List<Tiered> candidates) {
    }

    private record AchievementText(String headline, String detail, String statLabel, Double statValue) {
    }

    public record SpotlightHistoryEntry(
            LocalDate gameDate,
            PlayerSpotlightInsightType insightType,
            String headline,
            String detail,
            String statLabel,
            Double statValue
    ) {
    }

    public record SpotlightResponse(
            String steamId,
            String personaName,
            String avatar,
            PlayerSpotlightInsightType insightType,
            String headline,
            String detail,
            String statLabel,
            Double statValue
    ) {
    }
}
