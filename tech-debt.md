# Tech Debt

## Season Unique Constraint (data integrity)

**Problem:** `SeasonService.ensureSeasonForDate()` has a TOCTOU race condition. Under default `READ_COMMITTED` isolation, two concurrent requests can both find no matching season and both call `createSeasonsUntil()`, producing duplicate season rows for the same date range. Duplicate seasons silently corrupt leaderboard and award data.

**Requires:** A unique DB constraint on the `seasons` table to make any duplicate insert fail loudly.

**SQL to run manually:**
```sql
-- Ensure no existing duplicates first:
SELECT start_date, end_date, COUNT(*) FROM seasons GROUP BY start_date, end_date HAVING COUNT(*) > 1;

-- Add unique constraint:
ALTER TABLE seasons ADD CONSTRAINT uq_seasons_dates UNIQUE (start_date, end_date);
```

**Service-side handling** (add after applying the constraint):

In `SeasonService.ensureSeasonForDate()`, catch `DataIntegrityViolationException` and re-query:
```java
@Transactional
public Season ensureSeasonForDate(LocalDate date) {
    return seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date)
            .orElseGet(() -> {
                try {
                    return createSeasonsUntil(date);
                } catch (DataIntegrityViolationException e) {
                    // concurrent request created the season; re-query
                    return seasonRepository
                            .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date)
                            .orElseThrow(() -> new IllegalStateException("Season missing after conflict", e));
                }
            });
}
```
