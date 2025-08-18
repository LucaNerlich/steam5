-- Equal-width (range-based) buckets: split [min_total, max_total] into 5 bins of similar width.
-- Counts per bucket may differ (fair ranges, not equal counts).
WITH r AS (
    SELECT (total_positive + total_negative) AS total
    FROM steam_app_reviews
    WHERE (total_positive + total_negative) > 0
),
     ext AS (
         SELECT MIN(total)::bigint AS mn, MAX(total)::bigint AS mx
         FROM r
     ),
     bins AS (
         SELECT gs AS idx,
                ext.mn,
                ext.mx,
                CEIL(((ext.mx - ext.mn + 1)::numeric) / 5.0)::bigint AS width
         FROM ext, generate_series(1, 5) AS gs
     ),
     bounds AS (
         SELECT
             idx,
             (mn + (idx - 1) * width) AS lower,
             CASE WHEN idx < 5 THEN (mn + idx * width - 1) ELSE mx END AS upper
         FROM bins
     ),
     counts AS (
         SELECT
             b.idx,
             b.lower,
             b.upper,
             COUNT(r.total) AS count_in_bucket
         FROM bounds b
                  LEFT JOIN r ON r.total BETWEEN b.lower AND b.upper
         GROUP BY b.idx, b.lower, b.upper
     )
SELECT
    idx,
    lower,
    upper,
    (lower::bigint || '-' || upper::bigint) AS label,
    count_in_bucket
FROM counts
ORDER BY idx;
