package org.steam5.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.SteamAppReviews;
import org.steam5.domain.User;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.http.ReviewGameException;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.security.CurrentUser;
import org.steam5.service.ReviewGameStateService;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game")
public class ReviewGameStateController {

    private final ReviewGameStateService service;
    private final SteamAppDetailService detailService;
    private final GuessRepository guessRepository;
    private final SteamAppReviewsRepository reviewsRepository;
    private final org.steam5.repository.ReviewGamePickRepository pickRepository;
    private final Scheduler scheduler;
    private final MeterRegistry meterRegistry;

    // Live daily data — rounds regenerate at ~00:01 UTC; 30 min CDN window absorbs
    // traffic spikes while keeping staleness bounded. must-revalidate forbids any
    // cache from serving picks once they go stale. Deliberately no stale-while-
    // revalidate: it would let shared caches keep serving yesterday's picks during
    // background refresh, which is exactly the staleness this endpoint must avoid
    // (and it conflicts with must-revalidate anyway).
    private static final String CACHE_LIVE =
            "public, s-maxage=1800, max-age=60, must-revalidate";
    // Configuration data (bucket labels/ranges) — changes only on deploy.
    private static final String CACHE_CONFIG = "public, s-maxage=3600, max-age=300";
    // Historical round data — immutable once the day is over.
    private static final String CACHE_HISTORICAL = "public, max-age=31536000, immutable";
    // Per-user data — `private` keeps it out of shared/CDN caches so one user's
    // guesses are never served to another; only the user's own browser may store it.
    private static final String PRIVATE_LIVE = "private, max-age=60, must-revalidate";
    private static final String PRIVATE_HISTORICAL = "private, max-age=31536000, immutable";

    // Cache only successful 200 bodies: a request-specific 4xx/null result must never
    // occupy a shared key. Conditional 304s are answered by Spring MVC from the returned
    // 200 (see Etags), so the cached value stays request-independent.
    private static final String CACHE_ONLY_2XX =
            "#result == null || !#result.statusCode.is2xxSuccessful()";

    // Live "today" endpoints additionally must NOT cache an empty round. After the
    // 00:00 UTC key rollover, the new day's picks are generated lazily on the first
    // request; if that request races (lock contention / transient failure) and returns
    // an empty round, caching it would pin the "key advanced but not regenerated" state
    // for the whole day. Skipping the cache on empty keeps every request driving
    // generation until real picks exist, so only a fully-populated round is ever cached.
    private static final String CACHE_ONLY_2XX_NONEMPTY_PICKS =
            CACHE_ONLY_2XX + " || #result.body == null || #result.body.picks.isEmpty()";
    private static final String CACHE_ONLY_2XX_NONEMPTY_LIST =
            CACHE_ONLY_2XX + " || #result.body == null || #result.body.isEmpty()";

    private static int scorePoints(List<String> buckets, String selected, String actual) {
        int si = buckets.indexOf(selected);
        int ai = buckets.indexOf(actual);
        if (si < 0 || ai < 0) return 0;
        int d = Math.abs(si - ai);
        int max = 5;
        int step = 2;
        return Math.max(0, max - step * d);
    }

    private static String weakEtagForPicks(List<SteamAppDetail> details) {
        final List<String> fields = new java.util.ArrayList<>();
        for (SteamAppDetail d : details) {
            fields.add(String.valueOf(d.getAppId()));
            fields.add(d.getName());
            fields.add(d.getReleaseDate());
            if (d.getPriceOverview() != null) {
                fields.add(d.getPriceOverview().getCurrency());
                fields.add(Long.toString(d.getPriceOverview().getFinalAmount()));
            }
            // screenshot paths influence visual content; limit sample for speed
            int i = 0;
            if (d.getScreenshots() != null) for (var s : d.getScreenshots()) {
                if (i++ >= 2) break;
                fields.add(s.getPathFull());
            }
        }
        return Etags.weakEtag(fields);
    }

    private static String weakEtagForStringLists(List<? extends List<String>> lists) {
        return Etags.weakEtagForGroups(new java.util.ArrayList<>(lists));
    }

    @GetMapping("/days")
    // The body depends on `limit`, so the clamped limit is part of the key; a constant
    // key let a 5-day request and a 3650-day request (the sitemap) share one entry.
    @Cacheable(value = "review-game", key = "'days:' + T(Math).max(1, T(Math).min(#limit, 3650))", unless = CACHE_ONLY_2XX)
    public ResponseEntity<List<String>> listDays(@RequestParam(value = "limit", defaultValue = "60") int limit) {
        final int capped = Math.max(1, Math.min(limit, 3650));
        final List<LocalDate> dates = pickRepository.listDistinctPickDates(PageRequest.of(0, capped));
        final List<String> out = dates.stream().map(LocalDate::toString).toList();
        final String etag = weakEtagForStringLists(java.util.List.of(out));
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", "public, s-maxage=600, max-age=60")
                .body(out);
    }

    // Deliberately not @Cacheable: the response reveals the true answer for the
    // requested appId regardless of the caller's guess. Caching it by
    // (appId, bucketGuess) would let one caller's lookup answer every later
    // caller's request for the same appId for free.
    @PostMapping("/guess")
    public ResponseEntity<GuessResponse> submitGuess(@RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!service.getBucketLabels().contains(req.bucketGuess)) {
            return ResponseEntity.badRequest().build();
        }

        final int total = service.getTotalReviewCountForApp(req.appId);
        final String actual = service.inferBucket(total);
        // Correct means "this label names the bucket the app is in" — the same rule the
        // existing-guess branch of /guess-auth already applies
        // (actualBucket.equals(selectedBucket)). The range parser used here was a second,
        // divergent rule: it accepted the open-ended top label for a total of exactly its
        // boundary value, which inferBucket places in the closed bucket below.
        final boolean ok = req.bucketGuess.equals(actual);
        return ResponseEntity.ok(new GuessResponse(req.appId, total, actual, ok));
    }

    @GetMapping("/today/details")
    @Cacheable(value = "review-game", key = "'today-details:' + T(org.steam5.domain.GameDate).todayUtc()", unless = CACHE_ONLY_2XX_NONEMPTY_LIST)
    public ResponseEntity<List<SteamAppDetail>> getTodayDetails() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> details = detailService.findAllByAppIdIn(appIds);
        final String etag = weakEtagForPicks(details);
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", CACHE_LIVE)
                .body(details);
    }

    @GetMapping("/my/today")
    public ResponseEntity<?> myToday(@CurrentUser String steamId) {
        if (steamId == null) return ResponseEntity.status(401).build();

        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        // Only return guesses that belong to today's current picks. If picks were
        // regenerated for the same date, the user's earlier guesses remain dated
        // today but point at replaced games — exclude them so stale results are
        // never surfaced on the new picks.
        final Set<Long> currentAppIds = new HashSet<>();
        for (ReviewGamePick p : picks) currentAppIds.add(p.getAppId());

        final var guesses = guessRepository.findAllForDay(steamId, date).stream()
                .filter(g -> currentAppIds.contains(g.getAppId()))
                .toList();

        final Map<Long, Integer> totalReviewsByAppId = new HashMap<>(lookupTotalReviewsByAppId(guesses));
        totalReviewsByAppId.putAll(recordedTotalsByAppId(picks));
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                totalReviewsByAppId.getOrDefault(g.getAppId(), 0)
        )).toList();
        // Per-user data: `private` prevents shared/CDN caches from ever storing it
        // (would otherwise risk serving one user's guesses to another).
        return ResponseEntity.ok()
                .header("Cache-Control", PRIVATE_LIVE)
                .body(dtos);
    }

    @GetMapping("/my/day/{date}")
    public ResponseEntity<?> myDay(@PathVariable("date") String date,
                                   @CurrentUser String steamId) {
        if (steamId == null) return ResponseEntity.status(401).build();

        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        final var guesses = guessRepository.findAllForDay(steamId, day);
        final Map<Long, Integer> totalReviewsByAppId = new HashMap<>(lookupTotalReviewsByAppId(guesses));
        totalReviewsByAppId.putAll(recordedTotalsByAppId(pickRepository.findByPickDate(day)));
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                totalReviewsByAppId.getOrDefault(g.getAppId(), 0)
        )).toList();

        final boolean isToday = day.equals(GameDate.todayUtc());
        // Per-user data: never let a shared/CDN cache store it (would leak one
        // user's guesses to another). `private` restricts caching to the caller's
        // own browser.
        final String cacheControl = isToday ? PRIVATE_LIVE : PRIVATE_HISTORICAL;

        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .body(dtos);
    }

    @GetMapping("/my/history")
    public ResponseEntity<?> myHistory(@RequestParam(value = "from", required = false) String from,
                                       @RequestParam(value = "to", required = false) String to,
                                       @CurrentUser String steamId) {
        if (steamId == null) return ResponseEntity.status(401).build();

        final LocalDate start;
        final LocalDate end;
        try {
            start = from == null || from.isBlank() ? LocalDate.of(1970, 1, 1) : LocalDate.parse(from);
            end = to == null || to.isBlank() ? GameDate.todayUtc() : LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
        if (end.isBefore(start)) return ResponseEntity.badRequest().build();

        final var guesses = guessRepository.findBySteamIdBetween(steamId, start, end);
        final Map<Long, Integer> totalReviewsByAppId = lookupTotalReviewsByAppId(guesses);
        final Map<String, Integer> recordedByDayApp =
                recordedTotalsByDayApp(pickRepository.findByPickDateBetween(start, end));
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                recordedByDayApp.getOrDefault(
                        g.getGameDate() + "|" + g.getAppId(),
                        totalReviewsByAppId.getOrDefault(g.getAppId(), 0))
        )).toList();

        return ResponseEntity.ok()
                .header("Cache-Control", "private, s-maxage=600, max-age=300")
                .body(dtos);
    }

    @GetMapping("/today")
    @Cacheable(value = "review-game", key = "'today-picks:' + T(org.steam5.domain.GameDate).todayUtc()", unless = CACHE_ONLY_2XX_NONEMPTY_PICKS)
    public ResponseEntity<ReviewGameStateDto> getToday() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailService.findAllByAppIdIn(appIds).stream().toList();
        // Ensure details preserve the appIds order so round indices match what the user sees
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail d : fetched) {
            byId.put(d.getAppId(), d);
        }
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match");
        }

        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        final String etag = weakEtagForPicks(details);
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", CACHE_LIVE)
                .body(new ReviewGameStateDto(date, service.getBucketLabels(), service.getBucketTitles(), details));
    }

    public record ReviewGameStateDto(LocalDate date, List<String> buckets, List<String> bucketTitles,
                                     List<SteamAppDetail> picks) {
    }

    @GetMapping("/next-challenge-time")
    public ResponseEntity<Map<String, Object>> getNextChallengeTime() {
        final Map<String, Object> result = new HashMap<>();
        final OffsetDateTime now = OffsetDateTime.now();
        
        try {
            // Query Quartz scheduler for the actual next fire time
            final TriggerKey triggerKey = new TriggerKey("ReviewGameStateJob_Trigger");
            final var trigger = scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                throw new SchedulerException("Trigger not found");
            }
            final Date nextFireTime = trigger.getNextFireTime();
            
            if (nextFireTime != null) {
                // Quartz Date represents an instant in time (UTC epoch milliseconds)
                // Convert to OffsetDateTime in UTC explicitly
                final OffsetDateTime nextChallenge = OffsetDateTime.ofInstant(
                    nextFireTime.toInstant(),
                    java.time.ZoneOffset.UTC
                );
                result.put("nextChallengeTime", nextChallenge.toString()); // ISO-8601 format in UTC (e.g., "2024-01-15T00:01:00Z")
            } else {
                // Fallback: calculate manually if trigger not found
                final OffsetDateTime todayAt010 = now.toLocalDate().atStartOfDay().atOffset(now.getOffset()).plusMinutes(1);
                final OffsetDateTime nextChallenge = now.isBefore(todayAt010) ? todayAt010 : todayAt010.plusDays(1);
                result.put("nextChallengeTime", nextChallenge.toString());
            }
        } catch (SchedulerException e) {
            // Fallback: calculate manually if scheduler query fails
            // Note: This assumes the cron runs at 00:01 UTC (which is 01:01 CET or 02:01 CEST)
            final java.time.ZonedDateTime nowUtc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
            final java.time.ZonedDateTime todayAt010Utc = nowUtc.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).plusMinutes(1);
            final java.time.ZonedDateTime nextChallengeUtc = nowUtc.isBefore(todayAt010Utc) ? todayAt010Utc : todayAt010Utc.plusDays(1);
            result.put("nextChallengeTime", nextChallengeUtc.toOffsetDateTime().toString());
        }
        
        result.put("serverTimezoneOffset", now.getOffset().getTotalSeconds() / 60); // minutes from UTC
        
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=60, max-age=30")
                .body(result);
    }

    public record BucketMeta(List<String> buckets, List<String> bucketTitles) {
    }

    public record GuessRequest(Long appId, String bucketGuess) {
    }

    public record GuessResponse(Long appId, int totalReviews, String actualBucket, boolean correct) {
    }

    public record MyGuessDto(int roundIndex, Long appId, String selectedBucket, String actualBucket, int totalReviews) {
    }

    public record AlwaysPickScore(int bucketIndex, String bucketLabel, double avgPoints, int rounds) {
    }

    // Deliberately NOT @Transactional: the duplicate-key violation caught below
    // escapes the repository's participating transaction, Spring marks the
    // handler's transaction rollback-only, and the commit at method exit throws
    // UnexpectedRollbackException — discarding the idempotent 200 built in the
    // catch. Without an ambient transaction each repository call commits on its
    // own, and the unique index alone guards concurrency.
    @PostMapping("/guess-auth")
    public ResponseEntity<GuessResponse> submitGuessAuthenticated(@CurrentUser String steamId,
                                                                  @RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }
        // A guess persisted with a label outside the known bucket set can never
        // parse as numeric in downstream native aggregation queries (e.g.
        // GuessRepository#aggregateAllTimeStatsHavingMinRounds), which would
        // throw and break the nightly PlayerSpotlight job for every future run
        // until the row is removed. Reject before it ever reaches persistence.
        if (!service.getBucketLabels().contains(req.bucketGuess)) {
            return ResponseEntity.badRequest().build();
        }

        // No User bootstrap here: save() on a new entity with an assigned id is a merge, which
        // copies every null column over a row a concurrent logout/profile write just updated
        // (reverting token_not_valid_before). The login callback already creates the row, and
        // guesses has no FK to users.

        // date = today’s picks date
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final java.time.LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        // Grade from the answer recorded on the pick itself. steam_app_reviews is mutable
        // (the nightly refresh rewrites it), so recomputing here could score a round against
        // a different bucket than the one /my/today and the archive serve for it. Legacy rows
        // with no recorded answer fall back to the live count.
        final ReviewGamePick pick = picks.stream()
                .filter(p -> Objects.equals(p.getAppId(), req.appId))
                .findFirst()
                .orElse(null);
        final int total = pick != null && pick.getTotalReviews() != null
                ? pick.getTotalReviews()
                : service.getTotalReviewCountForApp(req.appId);
        final String computedActual = pick != null && pick.getActualBucket() != null
                ? pick.getActualBucket()
                : service.inferBucket(total);
        // Compute roundIndex strictly from ordered picks; default to -1 if not found
        final List<Long> pickOrder = picks.stream().map(ReviewGamePick::getAppId).toList();
        final int foundIdx = pickOrder.indexOf(req.appId);
        if (foundIdx < 0) {
            return ResponseEntity.badRequest().body(new GuessResponse(req.appId, total, computedActual, false));
        }
        final int roundIndex = foundIdx + 1;
        final int points = scorePoints(service.getBucketLabels(), req.bucketGuess, computedActual);

        // If exists, return existing without overwriting
        final var existingOpt = guessRepository.findBySteamIdAndGameDateAndRoundIndex(steamId, date, roundIndex);
        if (existingOpt.isPresent()) {
            final var g = existingOpt.get();
            final boolean alreadyOk = g.getActualBucket() != null && g.getActualBucket().equals(g.getSelectedBucket());
            return ResponseEntity.ok(new GuessResponse(g.getAppId(), total, g.getActualBucket(), alreadyOk));
        }

        // create new; guard against concurrent duplicate via the unique index
        try {
            guessRepository.save(new org.steam5.domain.Guess(null, steamId, date, roundIndex, req.appId, req.bucketGuess, computedActual, points, java.time.OffsetDateTime.now()));
            // Increment only when a brand-new guess is persisted so the
            // counter reflects player throughput, not duplicate submissions.
            Counter.builder("steam5.guesses")
                    .description("Authenticated guesses persisted, by exact-match outcome")
                    .tag("outcome", req.bucketGuess.equals(computedActual) ? "correct" : "incorrect")
                    .register(meterRegistry)
                    .increment();
        } catch (DataIntegrityViolationException ignored) {
            // Another request saved the same guess concurrently. The failed insert
            // rolled back only its own transaction (the handler is not
            // transactional), so this read — the repository's default read-only
            // transaction — sees the winner's committed row and returns it to
            // keep concurrent double-submits idempotent.
            final var existing = guessRepository.findBySteamIdAndGameDateAndRoundIndex(steamId, date, roundIndex);
            if (existing.isPresent()) {
                final var g = existing.get();
                final boolean alreadyOk = g.getActualBucket() != null && g.getActualBucket().equals(g.getSelectedBucket());
                return ResponseEntity.ok(new GuessResponse(g.getAppId(), total, g.getActualBucket(), alreadyOk));
            }
            // Conflict with no persisted row: never fall through to a success
            // response for a guess that was not saved.
            log.error("Duplicate-guess conflict resolved without a persisted row: steamId={} date={} roundIndex={}", steamId, date, roundIndex);
            // Empty 409: do not return totalReviews/actualBucket for a guess that
            // was not saved (that would leak today's answer for a retry).
            return ResponseEntity.status(409).build();
        }
        final boolean ok = req.bucketGuess.equals(computedActual);
        return ResponseEntity.ok(new GuessResponse(req.appId, total, computedActual, ok));
    }

    @GetMapping("/day/{date}")
    @Cacheable(value = "review-game", key = "'picks:' + #date", unless = CACHE_ONLY_2XX)
    public ResponseEntity<ReviewGameStateDto> getByDate(@PathVariable("date") String date) {
        final java.time.LocalDate day;
        try {
            day = java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        final List<ReviewGamePick> picks = pickRepository.findByPickDate(day);
        if (picks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailService.findAllByAppIdIn(appIds).stream().toList();
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail d : fetched) byId.put(d.getAppId(), d);
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match for day " + date);
        }
        final boolean isToday = day.equals(GameDate.todayUtc());
        final String etag = weakEtagForPicks(details);
        final String cc = isToday ? CACHE_LIVE : CACHE_HISTORICAL;
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", cc)
                .body(new ReviewGameStateDto(day, service.getBucketLabels(), service.getBucketTitles(), details));
    }

    public record DayAnswer(Long appId, int totalReviews, String actualBucket) {
    }

    /**
     * Reveals the review totals and buckets for a finished challenge day, so the
     * archive can show results without replaying a guess per pick.
     *
     * <p>Restricted to days strictly before today: the archive is for finished
     * days only, and keeping a live day's answers off this bulk endpoint avoids
     * handing out the current challenge's results through a dedicated route.</p>
     */
    @GetMapping("/day/{date}/answers")
    @Cacheable(value = "review-game", key = "'answers:' + #date", unless = CACHE_ONLY_2XX)
    public ResponseEntity<List<DayAnswer>> getDayAnswers(@PathVariable("date") String date) {
        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        if (!day.isBefore(GameDate.todayUtc())) {
            return ResponseEntity.notFound().build();
        }
        final List<ReviewGamePick> picks = pickRepository.findByPickDate(day);
        if (picks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final List<DayAnswer> answers = picks.stream()
                .map(p -> {
                    if (p.getTotalReviews() != null && p.getActualBucket() != null) {
                        return new DayAnswer(p.getAppId(), p.getTotalReviews(), p.getActualBucket());
                    }
                    // Legacy row: the answer was never recorded, so the current snapshot is
                    // the best available (same drift, frozen from the first generation on).
                    final int total = service.getTotalReviewCountForApp(p.getAppId());
                    return new DayAnswer(p.getAppId(), total, service.inferBucket(total));
                })
                .toList();
        return ResponseEntity.ok()
                .header("Cache-Control", CACHE_HISTORICAL)
                .body(answers);
    }

    @GetMapping("/buckets")
    @Cacheable(value = "one-day", key = "'buckets'")
    public ResponseEntity<BucketMeta> buckets() {
        final List<String> labels = service.getBucketLabels();
        final List<String> titles = service.getBucketTitles();
        final String etag = weakEtagForStringLists(java.util.List.of(labels, titles));
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", CACHE_CONFIG)
                .body(new BucketMeta(labels, titles));
    }

    public record ArchiveMonthDayPick(Long appId, String name) {
    }

    public record ArchiveMonthDay(String date, List<ArchiveMonthDayPick> picks) {
    }

    @GetMapping("/archive/month")
    @Cacheable(value = "review-game", key = "'archive-month:' + #month", unless = CACHE_ONLY_2XX_NONEMPTY_LIST)
    public ResponseEntity<List<ArchiveMonthDay>> archiveMonth(@RequestParam("month") String month) {
        final YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }

        final LocalDate from = ym.atDay(1);
        final LocalDate to = ym.plusMonths(1).atDay(1);
        final List<org.steam5.repository.ReviewGamePickRepository.MonthlyArchivePickRow> rows =
                pickRepository.listMonthlyArchivePicks(from, to);

        final Map<String, List<ArchiveMonthDayPick>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            final String date = row.getPickDate().toString();
            grouped.computeIfAbsent(date, k -> new ArrayList<>())
                    .add(new ArchiveMonthDayPick(row.getAppId(), row.getName()));
        }
        final List<ArchiveMonthDay> out = grouped.entrySet().stream()
                .map(e -> new ArchiveMonthDay(e.getKey(), e.getValue()))
                .toList();

        final boolean isCurrentMonth = ym.equals(YearMonth.now());
        final String cacheControl = isCurrentMonth
                ? "public, s-maxage=86400, max-age=3600"
                : "public, max-age=31536000, immutable";
        final String etag = weakEtagForStringLists(
                out.stream()
                        .map(day -> {
                            final ArrayList<String> lines = new ArrayList<>(day.picks().size() + 1);
                            lines.add(day.date());
                            for (ArchiveMonthDayPick pick : day.picks()) {
                                lines.add(pick.appId() + ":" + pick.name());
                            }
                            return lines;
                        })
                        .toList()
        );
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", cacheControl)
                .body(out);
    }

    // Deliberately not @Cacheable: the value is intentionally random, and caching it
    // (even briefly) would make every visitor land on the same day for the cache's
    // lifetime, defeating the point of a "random" archive link.
    @GetMapping("/archive/random")
    public ResponseEntity<Map<String, String>> randomArchiveDate() {
        final Optional<LocalDate> date = pickRepository.findRandomArchiveDate(GameDate.todayUtc());
        if (date.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("date", date.get().toString()));
    }

    public record HistoricalAlwaysPickResponse(String from, String to, List<AlwaysPickScore> scores) {
    }

    @GetMapping("/history/always-pick")
    @Cacheable(value = "review-game", key = "'always-pick-hist:' + (#from != null && !#from.isBlank() ? #from : 'min') + ':' + (#to != null && !#to.isBlank() ? #to : 'max')", unless = CACHE_ONLY_2XX)
    public ResponseEntity<HistoricalAlwaysPickResponse> alwaysPickHistory(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        // Malformed dates must 400, not 500. (Unbounded ranges are safe: the
        // computation iterates only pick dates that actually exist in range,
        // not every calendar day between from and to.)
        final java.time.LocalDate start;
        final java.time.LocalDate end;
        try {
            start = from == null || from.isBlank() ? java.time.LocalDate.of(1970, 1, 1) : java.time.LocalDate.parse(from);
            end = to == null || to.isBlank() ? GameDate.todayUtc() : java.time.LocalDate.parse(to);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
        // The response is cached under the raw from/to pair, so an unbounded EXplicit span lets a
        // caller mint one cache entry per enumerated range in the shared review-game cache.
        // An absent-or-blank range is the all-time view (its key is the 'min'/'max' literal pair,
        // so every blank spelling collapses onto it) and is left alone; any explicit span wider
        // than ~10 years is refused — the check runs on the parsed dates, so a one-sided request
        // (from-only or to-only) inherits the 1970/today default on the missing side and is bound
        // like a fully explicit span. The UI asks for one day at a time.
        final boolean allTime = (from == null || from.isBlank()) && (to == null || to.isBlank());
        if (!allTime && java.time.temporal.ChronoUnit.DAYS.between(start, end) > 3650) {
            return ResponseEntity.badRequest().build();
        }
        if (end.isBefore(start)) return ResponseEntity.badRequest().build();
        final var result = computeAlwaysPickForRange(start, end);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600")
                .body(new HistoricalAlwaysPickResponse(start.toString(), end.toString(), result.scores()));
    }

    private record AlwaysPickComputation(int rounds, List<AlwaysPickScore> scores) {
    }

    /**
     * Computes the AlwaysPickComputation for a given date range. This involves analyzing the
     * app IDs associated with review game picks within the specified range and computing
     * scores for each bucket label based on historical data.
     *
     * @param start The start date of the range for computation, inclusive.
     * @param end The end date of the range for computation, inclusive.
     * @return An instance of AlwaysPickComputation, containing the computed results including
     *         the number of rounds analyzed and the corresponding scores for each label.
     */
    private AlwaysPickComputation computeAlwaysPickForRange(java.time.LocalDate start, java.time.LocalDate end) {
        final List<String> labels = service.getBucketLabels();
        // Collect all appIds for dates in range, preserving chronological order per day
        final List<ReviewGamePick> picksInRange = pickRepository.findByPickDateBetween(start, end);
        final Map<LocalDate, List<ReviewGamePick>> picksByDate = new HashMap<>();
        for (ReviewGamePick pick : picksInRange) {
            picksByDate.computeIfAbsent(pick.getPickDate(), ignored -> new ArrayList<>()).add(pick);
        }
        picksByDate.values().forEach(picks -> picks.sort(
                // Match the player-visible round order (explicit round index,
                // legacy rows fall back to createdAt/id).
                Comparator.comparing(org.steam5.domain.ReviewGamePick::getRoundIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(org.steam5.domain.ReviewGamePick::getCreatedAt)
                        .thenComparing(org.steam5.domain.ReviewGamePick::getId)
        ));

        // Iterate only the pick dates that actually exist in range, in
        // chronological order. Iterating every calendar day between start and
        // end (potentially millions of empty days for to=9999-12-31) was an
        // anonymous CPU DoS; days without picks contribute nothing.
        final List<String> actualBuckets = new ArrayList<>();
        final List<Long> appIds = new ArrayList<>();
        final Set<Long> legacyAppIds = new HashSet<>();
        final Set<String> labelSet = new HashSet<>(labels);
        for (LocalDate d : picksByDate.keySet().stream().sorted().toList()) {
            final List<org.steam5.domain.ReviewGamePick> picks = picksByDate.get(d);
            for (org.steam5.domain.ReviewGamePick p : picks) {
                appIds.add(p.getAppId());
                if (p.getActualBucket() != null && labelSet.contains(p.getActualBucket())) {
                    // The answer recorded when the round was played stays authoritative.
                    actualBuckets.add(p.getActualBucket());
                } else if (p.getTotalReviews() != null) {
                    // Not a legacy row, but its label is from an older boundary set: map the
                    // recorded count under today's labels instead of returning an unknown label.
                    actualBuckets.add(service.inferBucket(p.getTotalReviews()));
                } else {
                    // Legacy row: no recorded answer, fall back to the current snapshot.
                    actualBuckets.add(null);
                    legacyAppIds.add(p.getAppId());
                }
            }
        }
        final Map<Long, Integer> legacyTotals = lookupTotalReviewsByAppIds(legacyAppIds);
        for (int i = 0; i < actualBuckets.size(); i++) {
            if (actualBuckets.get(i) == null) {
                actualBuckets.set(i, service.inferBucket(legacyTotals.getOrDefault(appIds.get(i), 0)));
            }
        }
        final int rounds = actualBuckets.size();
        final java.util.ArrayList<AlwaysPickScore> out = new java.util.ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            final String selected = labels.get(i);
            int sum = 0;
            for (String actual : actualBuckets) sum += scorePoints(labels, selected, actual);
            final double avg = rounds == 0 ? 0.0 : ((double) sum) / rounds;
            out.add(new AlwaysPickScore(i + 1, selected, avg, rounds));
        }
        return new AlwaysPickComputation(rounds, out);
    }

    /** Recorded totals for one day's picks, keyed by app id; legacy null answers are skipped. */
    private Map<Long, Integer> recordedTotalsByAppId(List<org.steam5.domain.ReviewGamePick> picks) {
        final Map<Long, Integer> totals = new HashMap<>();
        for (org.steam5.domain.ReviewGamePick p : picks) {
            if (p.getTotalReviews() != null) {
                totals.put(p.getAppId(), p.getTotalReviews());
            }
        }
        return totals;
    }

    /** Recorded totals across days, keyed by {@code pickDate|appId}; null answers are skipped. */
    private Map<String, Integer> recordedTotalsByDayApp(List<org.steam5.domain.ReviewGamePick> picks) {
        final Map<String, Integer> totals = new HashMap<>();
        for (org.steam5.domain.ReviewGamePick p : picks) {
            if (p.getTotalReviews() != null) {
                totals.put(p.getPickDate() + "|" + p.getAppId(), p.getTotalReviews());
            }
        }
        return totals;
    }

    private Map<Long, Integer> lookupTotalReviewsByAppId(List<org.steam5.domain.Guess> guesses) {
        final Set<Long> appIds = new HashSet<>();
        for (org.steam5.domain.Guess guess : guesses) {
            if (guess.getAppId() != null) {
                appIds.add(guess.getAppId());
            }
        }
        return lookupTotalReviewsByAppIds(appIds);
    }

    /**
     * Loads total review counts for the given app ids in one query. A per-appId
     * lookup here is an N+1: the range endpoints can span thousands of picks, and
     * each lookup also pushed a short-lived {@code review-count} entry into the
     * shared {@code review-game} cache, evicting the page entries.
     *
     * @param appIds the app ids to load; duplicates and unknown ids are harmless
     * @return totals by app id; ids without a reviews row are absent (callers default to 0)
     */
    private Map<Long, Integer> lookupTotalReviewsByAppIds(Collection<Long> appIds) {
        if (appIds.isEmpty()) {
            return Map.of();
        }

        final Map<Long, Integer> totalReviewsByAppId = new HashMap<>();
        for (SteamAppReviews reviews : reviewsRepository.findAllById(appIds)) {
            totalReviewsByAppId.put(reviews.getAppId(), reviews.getTotalPositive() + reviews.getTotalNegative());
        }
        return totalReviewsByAppId;
    }
}

