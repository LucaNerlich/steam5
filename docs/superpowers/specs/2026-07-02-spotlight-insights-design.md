# Player Spotlight: expanded insights & fairer selection

## Context

The Player Spotlight (`PlayerSpotlightService`, shipped on the `player-highlights` branch) shows one eligible player per day on round 1 of `/review-guesser`, picked from a priority ladder of "insight" tiers: `DAY_STREAK`, `WEEKLY_ACHIEVEMENT`, `HOT_STREAK`, `MILESTONE` (guaranteed fallback).

Two problems prompted this follow-up design:

1. **`WEEKLY_ACHIEVEMENT` is redundant.** It just re-surfaces the same per-timeframe achievements (`EARLY_BIRD`, `SHARPSHOOTER`, etc.) that already appear on the leaderboard — not novel, and not particularly "good vibes."
2. **Strict priority ordering lets one ambient tier dominate.** `DAY_STREAK` sits at priority #1 and qualifies for *any* player on an active ≥5-day streak. Since most regular players maintain a continuous streak, `DAY_STREAK` would win on most days, starving out rarer, more interesting stories even when they occur.

This design adds four new, more novel insight tiers and changes the selection mechanism so no single ambient tier can dominate, while still demoting (not removing) `WEEKLY_ACHIEVEMENT`.

## Selection mechanism

Tiers are split into two groups instead of one flat priority ladder:

1. **Competitive pool** — six tiers, evaluated every day: `DAY_STREAK`, `BEST_DAY_EVER`, `BEAT_THE_ODDS`, `WELCOME_BACK`, `MOST_IMPROVED`, `HOT_STREAK`. Every tier in this pool with ≥1 qualifying candidate is collected, then **one tier is picked uniformly at random** among just those qualifying tiers. A candidate is then picked from that tier using the existing date-seeded tie-break (sort by `steamId`, `Random(today.toEpochDay())`). The tier-pick and the candidate-pick both draw from that same `Random` instance, in that order, so the whole day's result is deterministic and reproducible from the date alone.
2. **Sequential fallbacks**, evaluated only if the competitive pool has zero qualifying tiers that day:
   - `WEEKLY_ACHIEVEMENT` — kept, but deliberately excluded from the lottery so it stays a low-visibility fallback rather than a full competitor.
   - `MILESTONE` — guaranteed last resort (always applicable for any eligible candidate), also excluded from the lottery so it never crowds out a more interesting story.

This directly fixes the dominance problem: on a day where `DAY_STREAK` and `MOST_IMPROVED` both have qualifying candidates, each has an equal chance instead of `DAY_STREAK` automatically winning. Event-based tiers (`BEST_DAY_EVER`, `BEAT_THE_ODDS`, `WELCOME_BACK`) don't need extra weighting to compete — they're naturally rare/self-limiting (true for at most a handful of players on any given day), so they only need to not be auto-beaten when they do occur. Weighting by rarity was considered and rejected for v1 as unnecessary tuning (YAGNI); revisit only if real usage shows a tier still crowds out others despite the lottery.

Eligibility (unchanged): ≥70 rounds all-time, played within the last 14 days.

## New tiers

All "yesterday"-based criteria use yesterday specifically, not "today" — the nightly job runs at 00:15 UTC, before any of the current day's rounds are played, so `GameDate.todayUtc()` at compute time has no guesses yet.

### BEST_DAY_EVER
Sum of yesterday's points is strictly greater than every one of the player's prior days' point-sums. Requires ≥2 prior days played, so a brand-new veteran's second day can't trivially "win."
- Data: per-player, per-day point totals, grouped from that player's full `Guess` history (`findBySteamIdBetween` or a new lightweight aggregate query — implementer's choice based on what's cheaper against the existing schema).

### BEAT_THE_ODDS
Among yesterday's rounds, find the one with the lowest average score across all players, using the existing `GuessRepository.findRoundAvgScoresInRange` (already filtered to ≥5 players for statistical relevance). That round only counts as "hard" if its average score is < 2.0/5. A candidate qualifies if their own points on that specific round were ≥4.
- Data: `findRoundAvgScoresInRange(yesterday, yesterday)` + the candidate's own `Guess` for that `(gameDate, roundIndex)`.

### WELCOME_BACK
Across the player's full play history, their two most recent distinct play-dates have a gap ≥4 days between them, **and** their average points on the most recent play-day are ≥3.0/5 (a genuinely good return, not a flop).
- Data: the unbounded `datesDesc` history already fetched per candidate during eligibility filtering, plus that day's `Guess` rows for the points check.

### MOST_IMPROVED
Average points over the last 30 days is both ≥15% relatively higher and ≥0.3 points higher than the average over the prior 30-day window (days 31–60 back). Requires ≥10 rounds played in each of the two windows, to avoid noise from sparse data.
- Data: `findBySteamIdBetween` for the two 30-day windows.

## MILESTONE tier polish

Replace the current generic "has played N rounds and counting, averaging X pts/round" text with **nice-number framing**: if the player's round count or lifetime point total is at or within a small trailing window (e.g. within 5) of a notable number (100/250/500/1000 rounds; 1000/5000/10000 lifetime points), lead with that milestone instead. Otherwise, fall back to the current generic phrasing. Still the guaranteed last-resort tier either way — this is a text-quality change only, not a new eligibility path.

## Edge cases

- Multiple candidates qualify for the tier the lottery picked → existing date-seeded tie-break applies unchanged.
- A candidate could qualify for more than one competitive tier simultaneously (e.g. both `BEST_DAY_EVER` and `WELCOME_BACK`) — irrelevant, since only the lottery-winning tier is evaluated/used; no cross-tier deduping needed.
- Exactly one qualifying tier in the competitive pool on a given day → that tier is used deterministically; the mechanism still works, "randomness" just has nothing to do that day.
- Zero qualifying tiers in the competitive pool → falls through to `WEEKLY_ACHIEVEMENT`, then `MILESTONE`, unchanged from the original design.

## Out of scope for this iteration

Ideas raised during brainstorming but deferred:
- Leaderboard-rank-movement stories (cracked top 10, climbed N spots) — explicitly de-scoped since rank is already visible on the leaderboard itself.
- Personal accuracy high (rolling hit-rate personal best).
- No-flop run (streak of rounds without a 0-point score).
- Tenure/anniversary and lifetime-points milestones as their own dedicated tiers (partially covered by the `MILESTONE` text polish above, but not a full dedicated tier).

These can be picked up in a future iteration if the current set doesn't feel varied enough in practice.
