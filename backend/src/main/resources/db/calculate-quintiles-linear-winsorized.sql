WITH r AS (
    SELECT (total_positive + total_negative) AS total
    FROM steam_app_reviews
    WHERE (total_positive + total_negative) > 0
),
     ext AS (
         SELECT
             MIN(total)::bigint                        AS mn,
             CAST(percentile_disc(0.995) WITHIN GROUP (ORDER BY total) AS bigint) AS cap
         FROM r
     ),
     -- Same edge formula as ReviewsBucketRepository.linearWinsorized(); the last bucket
     -- stays open-ended (upper NULL) and empty buckets are dropped below.
     edges AS (
         SELECT i, ext.mn + ((ext.cap - ext.mn + 1) * i) / 5 AS edge
         FROM ext, generate_series(0, 5) AS i
     ),
     bounds AS (
         SELECT
             e0.i + 1 AS idx,
             e0.edge AS lower,
             CASE WHEN e0.i < 4 THEN e1.edge - 1 ELSE NULL END AS upper
         FROM edges e0
                  JOIN edges e1 ON e1.i = e0.i + 1
         WHERE e0.i BETWEEN 0 AND 4
     ),
     counts AS (
         SELECT
             b.idx,
             b.lower,
             b.upper,
             COUNT(CASE
                       WHEN b.idx < 5 AND r.total BETWEEN b.lower AND b.upper THEN 1
                       WHEN b.idx = 5 AND r.total >= b.lower THEN 1
                 END) AS count_in_bucket
         FROM bounds b
                  CROSS JOIN r
         GROUP BY b.idx, b.lower, b.upper
     )
SELECT
    idx,
    lower,
    CASE WHEN idx < 5 THEN upper ELSE NULL END AS upper,
    CASE
        WHEN idx < 5 THEN (lower::text || '-' || upper::text)
        ELSE ('≥ ' || lower::text)
        END AS label,
    count_in_bucket
FROM counts
WHERE upper IS NULL OR lower <= upper
ORDER BY idx;
