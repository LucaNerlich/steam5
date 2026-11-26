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
     -- Same monotonic edge formula as ReviewsBucketRepository.equalWidth(): a fixed
     -- CEIL width can put bucket 5's lower edge past mx ("13-11"), so derive edges 0..5.
     edges AS (
         SELECT i, ext.mn + ((ext.mx - ext.mn + 1) * i) / 5 AS edge
         FROM ext, generate_series(0, 5) AS i
     ),
     bounds AS (
         SELECT
             e0.i + 1 AS idx,
             e0.edge AS lower,
             e1.edge - 1 AS upper
         FROM edges e0
                  JOIN edges e1 ON e1.i = e0.i + 1
         WHERE e0.i BETWEEN 0 AND 4
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
WHERE lower <= upper
ORDER BY idx;
